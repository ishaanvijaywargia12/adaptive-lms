package com.lms.kafka.consumer;

import com.lms.kafka.event.RagDoubtSubmittedEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.ai.service.RagQueryService;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the RAG doubt-resolution pipeline.
 * <p>
 * Listens on {@code lms.rag.doubt.submitted} and delegates to
 * {@link RagQueryService} to perform:
 * <ol>
 *   <li>Embed the student's question via OpenAI text-embedding-ada-002</li>
 *   <li>Retrieve top-K chunks from Qdrant filtered by tenantId + courseId</li>
 *   <li>Generate a structured answer via gpt-4o-mini</li>
 *   <li>Cache the answer in Redis (SHA-256 keyed)</li>
 *   <li>Persist to PostgreSQL {@code doubt_sessions}</li>
 *   <li>Notify the student via the existing notification pipeline</li>
 * </ol>
 * Failures are caught and routed to {@code lms.rag.doubt.dlq}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RagDoubtConsumer {

    private final RagQueryService ragQueryService;
    private final KafkaProducerService kafkaProducerService;

    @KafkaListener(
            topics = "lms.rag.doubt.submitted",
            groupId = "lms-rag-query-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onDoubtSubmitted(RagDoubtSubmittedEvent event) {
        TenantContext.setCurrentTenant(event.getTenantId());
        try {
            log.info("[RAG-QUERY] START eventId={} sessionId={} tenantId={} courseId={}",
                    event.getEventId(), event.getSessionId(), event.getTenantId(), event.getCourseId());

            ragQueryService.resolveDoubt(event);

            log.info("[RAG-QUERY] DONE eventId={} sessionId={}", event.getEventId(), event.getSessionId());
        } catch (Exception ex) {
            log.error("[RAG-QUERY] FAILED eventId={} sessionId={} reason={} — routing to DLQ",
                    event.getEventId(), event.getSessionId(), ex.getMessage(), ex);
            kafkaProducerService.publishDoubtDlq(event);
        } finally {
            TenantContext.clear();
        }
    }
}
