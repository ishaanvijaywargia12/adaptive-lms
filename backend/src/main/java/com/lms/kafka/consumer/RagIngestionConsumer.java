package com.lms.kafka.consumer;

import com.lms.kafka.event.RagDocumentIngestionEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.ai.service.DocumentIngestionService;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the RAG document ingestion pipeline.
 * <p>
 * Listens on {@code lms.rag.document.ingestion.requested} and delegates to
 * {@link DocumentIngestionService} to perform:
 * <ol>
 *   <li>PDF download from MinIO</li>
 *   <li>Text extraction via Apache Tika</li>
 *   <li>Sliding-window chunking</li>
 *   <li>OpenAI embedding via text-embedding-ada-002</li>
 *   <li>Upsert to Qdrant with tenant-aware metadata payload</li>
 * </ol>
 * Failures are caught and routed to {@code lms.rag.document.ingestion.dlq} for
 * manual inspection and replay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RagIngestionConsumer {

    private final DocumentIngestionService documentIngestionService;
    private final KafkaProducerService kafkaProducerService;

    @KafkaListener(
            topics = "lms.rag.document.ingestion.requested",
            groupId = "lms-rag-ingestion-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDocumentIngestion(RagDocumentIngestionEvent event) {
        TenantContext.setCurrentTenant(event.getTenantId());
        try {
            log.info("[RAG-INGEST] START eventId={} tenantId={} courseId={} file={}",
                    event.getEventId(), event.getTenantId(), event.getCourseId(), event.getOriginalFilename());

            documentIngestionService.ingest(event);

            log.info("[RAG-INGEST] DONE eventId={}", event.getEventId());
        } catch (Exception ex) {
            log.error("[RAG-INGEST] FAILED eventId={} reason={} — routing to DLQ",
                    event.getEventId(), ex.getMessage(), ex);
            kafkaProducerService.publishIngestionDlq(event);
        } finally {
            TenantContext.clear();
        }
    }
}
