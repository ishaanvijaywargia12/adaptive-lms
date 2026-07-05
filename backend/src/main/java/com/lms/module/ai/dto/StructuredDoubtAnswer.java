package com.lms.module.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Structured answer produced by the RAG pipeline and stored in Redis + PostgreSQL.
 * <p>
 * The {@code answer} field is an LLM-generated response structured as:
 * <ol>
 *   <li>A direct, concise answer (2–3 sentences)</li>
 *   <li>Key concepts explained</li>
 *   <li>A practical example (when applicable)</li>
 * </ol>
 *
 * <p>{@code sourceChunks} holds the raw Qdrant retrieval results for explainability
 * and can be surfaced to the student as "Sources".
 *
 * <p>Implements {@link Serializable} for Redis serialization compatibility.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StructuredDoubtAnswer implements Serializable {

    /** Reference to the {@code doubt_sessions} row. */
    private UUID sessionId;

    /** The original question as submitted by the student. */
    private String questionText;

    /** LLM-generated, structured answer. */
    private String answer;

    /**
     * Top-K raw text chunks retrieved from Qdrant that were injected into the prompt.
     * Enables "View Sources" UX on the frontend.
     */
    private List<String> sourceChunks;

    /** Timestamp when the answer was generated and stored. */
    private LocalDateTime resolvedAt;

    // ─── Factory Methods ─────────────────────────────────────────────────────

    /**
     * Returns a graceful fallback answer when no relevant chunks are found in Qdrant
     * (e.g., the course material has not been indexed yet, or the question is off-topic).
     */
    public static StructuredDoubtAnswer noContext() {
        return StructuredDoubtAnswer.builder()
                .answer("No relevant course material was found for your question. "
                      + "This could mean the course content hasn't been indexed yet, "
                      + "or your question may be outside the scope of this course. "
                      + "Please try rephrasing or contact your instructor directly.")
                .sourceChunks(List.of())
                .resolvedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Returns a failure answer when the RAG pipeline encounters an unrecoverable error.
     */
    public static StructuredDoubtAnswer failed(UUID sessionId) {
        return StructuredDoubtAnswer.builder()
                .sessionId(sessionId)
                .answer("We were unable to process your doubt at this time. "
                      + "Please try again in a few minutes or contact support.")
                .sourceChunks(List.of())
                .resolvedAt(LocalDateTime.now())
                .build();
    }
}
