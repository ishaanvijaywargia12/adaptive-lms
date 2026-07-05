package com.lms.kafka.consumer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface KafkaProcessedEventRepository extends JpaRepository<KafkaProcessedEvent, UUID> {
    boolean existsByEventId(String eventId);
}
