package com.lms.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Kafka event published when a student submits a doubt question.
 * <p>
 * Consumed by {@code RagDoubtConsumer} to trigger the RAG query pipeline:
 * embed question → Qdrant topK search (tenant+course filtered) → LLM generation → persist + notify.
 *
 * <p><b>Topic:</b> {@code lms.rag.doubt.submitted}
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RagDoubtSubmittedEvent extends BaseEvent {

    /** Reference to the {@code doubt_sessions} row created before publishing this event. */
    private UUID sessionId;

    /** Student who submitted the doubt — used to deliver the answer notification. */
    private UUID studentId;

    /**
     * Course whose material should be searched in Qdrant.
     * Enforced as a {@code must} filter alongside {@code tenantId}.
     */
    private UUID courseId;

    /** Raw question text from the student. */
    private String questionText;
}
