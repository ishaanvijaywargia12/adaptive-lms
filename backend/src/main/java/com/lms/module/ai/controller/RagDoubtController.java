package com.lms.module.ai.controller;

import com.lms.common.response.ApiResponse;
import com.lms.kafka.event.RagDocumentIngestionEvent;
import com.lms.kafka.event.RagDoubtSubmittedEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.ai.dto.DoubtSubmissionRequest;
import com.lms.module.ai.dto.DoubtSubmissionResponse;
import com.lms.module.ai.dto.StructuredDoubtAnswer;
import com.lms.module.ai.entity.DoubtSession;
import com.lms.module.ai.repository.DoubtSessionRepository;
import com.lms.module.ai.service.RagCacheService;
import com.lms.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller exposing the RAG-based doubt resolution API.
 *
 * <p><b>Endpoints:</b>
 * <ul>
 *   <li>{@code POST /api/v1/rag/doubts} — Submit a student doubt</li>
 *   <li>{@code GET  /api/v1/rag/doubts/{sessionId}} — Poll doubt status + answer</li>
 *   <li>{@code GET  /api/v1/rag/doubts/my} — Student's own doubt history</li>
 *   <li>{@code POST /api/v1/rag/materials/{courseId}/ingest} — Trigger PDF ingestion</li>
 * </ul>
 *
 * <p><b>Caching strategy:</b> The POST endpoint checks Redis before publishing to Kafka.
 * On a cache hit the answer is returned synchronously with HTTP 200 (typically &lt;20 ms).
 * On a cache miss the doubt is persisted as PENDING and Kafka publishes it for async processing;
 * the client receives HTTP 202 with a {@code sessionId} to poll or await a WebSocket push.
 */
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "RAG Doubt Resolution", description = "AI-powered doubt resolution using vector semantic search")
public class RagDoubtController {

    private final KafkaProducerService kafkaProducerService;
    private final RagCacheService ragCacheService;
    private final DoubtSessionRepository doubtSessionRepository;

    // ─── Doubt Submission ─────────────────────────────────────────────────────

    /**
     * Submits a student doubt for RAG-based resolution.
     *
     * <p>Flow:
     * <ol>
     *   <li>Check Redis cache with SHA-256(tenantId+courseId+normalizedQuestion).</li>
     *   <li>Cache HIT → return cached {@link StructuredDoubtAnswer} immediately (HTTP 200).</li>
     *   <li>Cache MISS → persist {@code doubt_sessions} row as PENDING,
     *       publish {@code lms.rag.doubt.submitted} event, return HTTP 202 with sessionId.</li>
     * </ol>
     *
     * @param request   Validated request body with courseId + question
     * @param jwt       Keycloak JWT carrying student's sub (UUID)
     * @return 200 with {@link StructuredDoubtAnswer} (cache hit) or
     *         202 with {@link DoubtSubmissionResponse} (async, cache miss)
     */
    @PostMapping("/doubts")
    @Operation(summary = "Submit a doubt question for AI resolution")
    public ResponseEntity<ApiResponse<?>> submitDoubt(
            @Valid @RequestBody DoubtSubmissionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String tenantId  = TenantContext.getCurrentTenant();
        UUID   studentId = UUID.fromString(jwt.getSubject());

        log.info("[RAG-CTRL] Doubt submitted by studentId={} courseId={}", studentId, request.courseId());

        // 1. Cache check — synchronous fast path
        Optional<StructuredDoubtAnswer> cached =
                ragCacheService.get(tenantId, request.courseId(), request.question());
        if (cached.isPresent()) {
            log.debug("[RAG-CTRL] Cache HIT for studentId={}", studentId);
            return ResponseEntity.ok(ApiResponse.success(cached.get()));
        }

        // 2. Persist session as PENDING
        DoubtSession session = doubtSessionRepository.save(
                DoubtSession.builder()
                        .studentId(studentId)
                        .courseId(request.courseId())
                        .questionText(request.question())
                        .build()
        );

        // 3. Publish to Kafka for async RAG processing
        kafkaProducerService.publishDoubtSubmittedEvent(
                RagDoubtSubmittedEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .tenantId(tenantId)
                        .occurredAt(LocalDateTime.now())
                        .sessionId(session.getId())
                        .studentId(studentId)
                        .courseId(request.courseId())
                        .questionText(request.question())
                        .build()
        );

        return ResponseEntity.accepted()
                .body(ApiResponse.success(new DoubtSubmissionResponse(
                        session.getId(),
                        "Your doubt is being processed. You will be notified when the answer is ready."
                )));
    }

    // ─── Doubt Status Polling ─────────────────────────────────────────────────

    /**
     * Returns the current status and answer (when resolved) for a doubt session.
     * Frontend polls this endpoint or uses it as fallback if WebSocket push is missed.
     */
    @GetMapping("/doubts/{sessionId}")
    @Operation(summary = "Poll the status and answer of a doubt session")
    public ResponseEntity<ApiResponse<DoubtSession>> getDoubtStatus(@PathVariable UUID sessionId) {
        return doubtSessionRepository.findById(sessionId)
                .map(session -> ResponseEntity.ok(ApiResponse.success(session)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns all doubt sessions submitted by the authenticated student, newest first.
     */
    @GetMapping("/doubts/my")
    @Operation(summary = "Get authenticated student's doubt history")
    public ResponseEntity<ApiResponse<List<DoubtSession>>> getMyDoubts(@AuthenticationPrincipal Jwt jwt) {
        UUID studentId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(
                doubtSessionRepository.findByStudentIdOrderByCreatedAtDesc(studentId)
        ));
    }


    // ─── Document Ingestion Trigger ───────────────────────────────────────────

    /**
     * Instructor-facing endpoint to trigger PDF ingestion into the RAG vector store.
     *
     * <p>The PDF must already be stored in MinIO. This endpoint simply publishes
     * the {@code lms.rag.document.ingestion.requested} Kafka event; all heavy lifting
     * (Tika extraction, chunking, embedding, Qdrant upsert) happens asynchronously
     * in {@code RagIngestionConsumer}.
     *
     * @param courseId  Course the material belongs to
     * @param minioKey  MinIO object key of the already-uploaded PDF
     * @param filename  Original filename for logging/display
     * @return 202 Accepted
     */
    @PostMapping("/materials/{courseId}/ingest")
    @Operation(summary = "Trigger RAG indexing for an already-uploaded PDF material")
    public ResponseEntity<String> triggerIngestion(
            @PathVariable UUID courseId,
            @RequestParam String minioKey,
            @RequestParam(defaultValue = "document.pdf") String filename,
            @AuthenticationPrincipal Jwt jwt) {

        String tenantId = TenantContext.getCurrentTenant();

        log.info("[RAG-CTRL] Ingestion triggered by instructorId={} courseId={} key={}",
                jwt.getSubject(), courseId, minioKey);

        kafkaProducerService.publishDocumentIngestionEvent(
                RagDocumentIngestionEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .tenantId(tenantId)
                        .occurredAt(LocalDateTime.now())
                        .courseId(courseId)
                        .minioKey(minioKey)
                        .originalFilename(filename)
                        .build()
        );

        return ResponseEntity.accepted()
                .body("Document ingestion started. Chunks will be indexed into Qdrant asynchronously.");
    }
}
