package com.lms.kafka.consumer;

import com.lms.module.quiz.entity.QuizAttempt;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Ensures Kafka events are processed exactly once.
 * Stores event IDs in kafka_processed_events table per-tenant schema.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventIdempotencyService {

    private final KafkaProcessedEventRepository eventRepository;

    public boolean isProcessed(String eventId) {
        return eventRepository.existsByEventId(eventId);
    }

    @Transactional
    public void markProcessed(String eventId, String topic) {
        if (!eventRepository.existsByEventId(eventId)) {
            KafkaProcessedEvent event = new KafkaProcessedEvent();
            event.setEventId(eventId);
            event.setTopic(topic);
            event.setProcessedAt(LocalDateTime.now());
            eventRepository.save(event);
        }
    }
}
