package com.lms.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.kafka.event.BaseEvent;
import com.lms.kafka.event.RagDoubtSubmittedEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.ai.dto.StructuredDoubtAnswer;
import com.lms.module.ai.entity.DoubtStatus;
import com.lms.module.ai.repository.DoubtSessionRepository;
import com.lms.module.ai.repository.QdrantVectorRepository;
import com.lms.module.ai.repository.QdrantVectorRepository.VectorChunkResult;
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
import java.util.stream.Collectors;

/**
 * Core RAG (Retrieval-Augmented Generation) orchestrator.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagQueryService {

    @Value("${rag.top-k:5}")
    private int topK;

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
    private final ObjectMapper objectMapper;

    @Transactional
    public void resolveDoubt(RagDoubtSubmittedEvent event) {
        log.info("[RAG] Resolving doubt sessionId={} tenantId={} courseId={}",
                event.getSessionId(), event.getTenantId(), event.getCourseId());

        try {
            // Step 1: Embed the question (384-dim AllMiniLmL6V2)
            Embedding questionEmbedding = embeddingModel.embed(event.getQuestionText()).content();

            // Step 2: Retrieve top-K chunks from Qdrant with source details
            List<VectorChunkResult> retrievedChunks = qdrantVectorRepository.searchTopKWithDetails(
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
                persistAndNotify(event, fallback, List.of());
                return;
            }

            // Step 4: Build numbered context block for the prompt
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = 0; i < retrievedChunks.size(); i++) {
                VectorChunkResult chunk = retrievedChunks.get(i);
                contextBuilder.append("[Excerpt ").append(i + 1).append(" (Source: ").append(chunk.getObjectKey()).append(")]\n")
                        .append(chunk.getText())
                        .append("\n\n");
            }
            String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, contextBuilder);
            String fullPrompt   = systemPrompt + "\nStudent Question: " + event.getQuestionText();

            // Step 5: LLM generation via Gemini Flash
            log.debug("[RAG] Calling LLM for sessionId={}", event.getSessionId());
            String rawAnswer = chatLanguageModel.generate(fullPrompt);

            List<String> chunkTexts = retrievedChunks.stream()
                    .map(VectorChunkResult::getText)
                    .collect(Collectors.toList());

            // Step 6: Build structured answer
            StructuredDoubtAnswer answer = StructuredDoubtAnswer.builder()
                    .sessionId(event.getSessionId())
                    .questionText(event.getQuestionText())
                    .answer(rawAnswer)
                    .sourceChunks(chunkTexts)
                    .resolvedAt(LocalDateTime.now())
                    .build();

            // Step 7: Cache in Redis (24 h TTL)
            ragCacheService.put(event.getTenantId(), event.getCourseId(), event.getQuestionText(), answer);

            // Steps 8 & 9: Persist + Notify
            persistAndNotify(event, answer, retrievedChunks);

        } catch (Exception ex) {
            log.error("[RAG] Pipeline failed for sessionId={}: {}", event.getSessionId(), ex.getMessage(), ex);
            markSessionFailed(event.getSessionId(), ex.getMessage());
        }
    }

    private void persistAndNotify(RagDoubtSubmittedEvent event, StructuredDoubtAnswer answer, List<VectorChunkResult> sources) {
        doubtSessionRepository.findById(event.getSessionId()).ifPresent(session -> {
            session.setAnswerText(answer.getAnswer());
            try {
                session.setSourcesJson(objectMapper.writeValueAsString(sources));
            } catch (Exception e) {
                log.warn("[RAG] Could not serialize sources for sessionId={}", event.getSessionId());
            }
            session.setStatus(DoubtStatus.RESOLVED);
            session.setResolvedAt(answer.getResolvedAt());
            doubtSessionRepository.save(session);
            log.info("[RAG] Session {} marked RESOLVED", event.getSessionId());
        });

        kafkaProducerService.publishNotification(
                new BaseEvent.NotificationSendEvent(
                        UUID.randomUUID().toString(),
                        event.getTenantId(),
                        LocalDateTime.now(),
                        event.getStudentId(),
                        "Your doubt has been resolved! 🤖",
                        "AI has answered your question about this course.",
                        "RAG_ANSWER",
                        "{\"sessionId\":\"" + event.getSessionId() + "\"}"
                )
        );
    }

    private void markSessionFailed(UUID sessionId, String error) {
        doubtSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus(DoubtStatus.FAILED);
            session.setErrorMessage(error);
            doubtSessionRepository.save(session);
        });
    }
}
