package com.lms.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * Kafka event published when an instructor uploads a PDF course material.
 * <p>
 * Consumed by {@code RagIngestionConsumer} to trigger the document embedding pipeline:
 * PDF download from MinIO → Apache Tika extraction → chunk → OpenAI embed → Qdrant upsert.
 *
 * <p><b>Topic:</b> {@code lms.rag.document.ingestion.requested}
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RagDocumentIngestionEvent extends BaseEvent {

    /** ID of the course this material belongs to. Used as Qdrant payload filter. */
    private UUID courseId;

    /**
     * MinIO object key of the uploaded PDF.
     * Example: {@code lms-content/tenant-a/course-uuid/lecture1.pdf}
     */
    private String minioKey;

    /** Original filename from the multipart upload, used for logging. */
    private String originalFilename;
}
