package com.lms.module.ai.service;

import com.lms.kafka.event.BaseEvent;
import com.lms.kafka.event.RagDoubtSubmittedEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.ai.dto.StructuredDoubtAnswer;
import com.lms.module.ai.entity.DoubtSession;
import com.lms.module.ai.entity.DoubtStatus;
import com.lms.module.ai.repository.DoubtSessionRepository;
import com.lms.module.ai.repository.QdrantVectorRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Core RAG (Retrieval-Augmented Generation) orchestrator.
 * <p>
 * Processes a {@link RagDoubtSubmittedEvent} through the full pipeline:
 * <ol>
 *   <li>Embed the student's question via OpenAI text-embedding-ada-002</li>
 *   <li>Retrieve top-K semantically relevant chunks from Qdrant
 *       (strictly filtered by tenantId + courseId)</li>
 *   <li>Inject retrieved context into a structured system prompt</li>
 *   <li>Generate a structured answer via gpt-4o-mini (LangChain4j)</li>
 *   <li>Cache the answer in Redis for 24 h (SHA-256 keyed)</li>
 *   <li>Persist the answer in PostgreSQL {@code doubt_sessions}</li>
 *   <li>Notify the student via the existing Kafka notification pipeline</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagQueryService {

    @Value("${rag.top-k:5}")
    private int topK;

    // ─── System prompt injected as the LLM context frame ──────────────────────
    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are an expert teaching assistant for an online learning platform.
            Your role is to help students understand their course material clearly and accurately.

            Using ONLY the course material excerpts provided below, answer the student's question.
            If the answer cannot be determined from the provided excerpts, say so explicitly.

            Structure your response exactly as follows:
            **Direct Answer:** (2–3 sentences directly addressing the question)
            **Key Concepts:** (brief explanation of the relevant concepts)
            **Example:** (a concrete practical example, if applicable)
            **Sources:** (note which excerpt number(s) informed your answer)

            --- Course Material Excerpts ---
            %s
            --- End of Excerpts ---
            """;

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatLanguageModel;
    private final QdrantVectorRepository qdrantVectorRepository;
    private final RagCacheService ragCacheService;
    private final DoubtSessionRepository doubtSessionRepository;
    private final KafkaProducerService kafkaProducerService;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Executes the full RAG pipeline for a submitted student doubt.
     * Called asynchronously by {@code RagDoubtConsumer}.
     *
     * @param event The Kafka event carrying sessionId, studentId, courseId, questionText
     */
    @Transactional
    public void resolveDoubt(RagDoubtSubmittedEvent event) {
        log.info("[RAG] Resolving doubt sessionId={} tenantId={} courseId={}",
                event.getSessionId(), event.getTenantId(), event.getCourseId());

        try {
            // Step 1: Embed the question
            Embedding questionEmbedding = embeddingModel.embed(event.getQuestionText()).content();
            log.debug("[RAG] Question embedded ({} dims)", questionEmbedding.vectorAsList().size());

            // Step 2: Retrieve top-K chunks from Qdrant (tenant + course isolated)
            List<String> retrievedChunks = qdrantVectorRepository.searchTopK(
                    event.getTenantId(),
                    event.getCourseId(),
                    questionEmbedding.vectorAsList(),
                    topK
            );

            // Step 3: Handle empty retrieval
            if (retrievedChunks.isEmpty()) {
                log.warn("[RAG] No relevant chunks found for sessionId={} courseId={}",
                        event.getSessionId(), event.getCourseId());
                StructuredDoubtAnswer fallback = StructuredDoubtAnswer.noContext();
                fallback.setSessionId(event.getSessionId());
                fallback.setQuestionText(event.getQuestionText());
                persistAndNotify(event, fallback);
                return;
            }

            // Step 4: Build numbered context block for the prompt
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < retrievedChunks.size(); i++) {
                contextBuilder.append("[Excerpt ").append(i + 1).append("]\n")
                        .append(retrievedChunks.get(i))
                        .append("\n\n");
            }
            String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, contextBuilder);
            String fullPrompt   = systemPrompt + "\nStudent Question: " + event.getQuestionText();

            // Step 5: LLM generation via LangChain4j → gpt-4o-mini
            log.debug("[RAG] Calling LLM for sessionId={} (context {} chars)", event.getSessionId(), fullPrompt.length());
            String rawAnswer = chatLanguageModel.generate(fullPrompt);
            log.debug("[RAG] LLM responded for sessionId={}", event.getSessionId());

            // Step 6: Build the structured answer object
            StructuredDoubtAnswer answer = StructuredDoubtAnswer.builder()
                    .sessionId(event.getSessionId())
                    .questionText(event.getQuestionText())
                    .answer(rawAnswer)
                    .sourceChunks(retrievedChunks)
                    .resolvedAt(LocalDateTime.now())
                    .build();

            // Step 7: Cache in Redis (24 h TTL)
            ragCacheService.put(event.getTenantId(), event.getCourseId(), event.getQuestionText(), answer);

            // Steps 8 & 9: Persist + Notify
            persistAndNotify(event, answer);

        } catch (Exception ex) {
            log.error("[RAG] Pipeline failed for sessionId={}: {}", event.getSessionId(), ex.getMessage(), ex);
            markSessionFailed(event.getSessionId());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void persistAndNotify(RagDoubtSubmittedEvent event, StructuredDoubtAnswer answer) {
        // Persist answer to PostgreSQL
        doubtSessionRepository.findById(event.getSessionId()).ifPresent(session -> {
            session.setAnswerText(answer.getAnswer());
            session.setStatus(DoubtStatus.RESOLVED);
            session.setResolvedAt(answer.getResolvedAt());
            doubtSessionRepository.save(session);
            log.info("[RAG] Session {} marked RESOLVED", event.getSessionId());
        });

        // Push notification to student via existing pipeline
        kafkaProducerService.publishNotification(
                new BaseEvent.NotificationSendEvent(
                        UUID.randomUUID().toString(),
                        event.getTenantId(),
                        LocalDateTime.now(),
                        event.getStudentId(),
                        "Your doubt has been resolved! 🤖",
                        "AI has answered your question about this course. Tap to view the answer.",
                        "RAG_ANSWER",
                        "{\"sessionId\":\"" + event.getSessionId() + "\"}"
                )
        );
    }

    private void markSessionFailed(UUID sessionId) {
        doubtSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus(DoubtStatus.FAILED);
            doubtSessionRepository.save(session);
        });
    }
}
