package com.lms.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic definitions.
 *
 * <p>Only activated when {@code lms.events.mode=kafka} (the default for local/Docker).
 * In the demo profile ({@code LMS_EVENTS_MODE=sync}), Kafka auto-configuration is
 * excluded entirely and this class produces no beans.
 *
 * <p>The {@code @ConditionalOnProperty} guards prevent Spring from trying to create
 * Kafka admin clients or topics when running without a broker.
 */
@Configuration
@org.springframework.kafka.annotation.EnableKafka
@ConditionalOnProperty(name = "lms.events.mode", havingValue = "kafka", matchIfMissing = true)
public class KafkaConfig {

    @Bean public NewTopic lessonCompleted()            { return topic("lms.lesson.completed", 3); }
    @Bean public NewTopic quizPassed()                 { return topic("lms.quiz.passed", 3); }
    @Bean public NewTopic quizFailed()                 { return topic("lms.quiz.failed", 3); }
    @Bean public NewTopic assignmentSubmitted()        { return topic("lms.assignment.submitted", 3); }
    @Bean public NewTopic assignmentGraded()           { return topic("lms.assignment.graded", 3); }
    @Bean public NewTopic courseCompleted()            { return topic("lms.course.completed", 3); }
    @Bean public NewTopic certificateIssued()          { return topic("lms.certificate.issued", 1); }
    @Bean public NewTopic badgeEarned()                { return topic("lms.badge.earned", 1); }
    @Bean public NewTopic liveSessionStarted()         { return topic("lms.live.session.started", 1); }
    @Bean public NewTopic liveSessionEnded()           { return topic("lms.live.session.ended", 1); }
    @Bean public NewTopic enrollmentConfirmed()        { return topic("lms.enrollment.confirmed", 3); }
    @Bean public NewTopic coursePublished()            { return topic("lms.course.published", 1); }
    @Bean public NewTopic notificationSend()           { return topic("lms.notification.send", 3); }

    // ─── RAG Topics ───────────────────────────────────────────────────────────
    @Bean public NewTopic ragDocumentIngestionRequested() { return topic("lms.rag.document.ingestion.requested", 3); }
    @Bean public NewTopic ragDocumentIngestionDlq()       { return topic("lms.rag.document.ingestion.dlq", 1); }
    @Bean public NewTopic ragDoubtSubmitted()              { return topic("lms.rag.doubt.submitted", 6); }
    @Bean public NewTopic ragDoubtDlq()                   { return topic("lms.rag.doubt.dlq", 1); }

    private NewTopic topic(String name, int partitions) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(1)
                .build();
    }
}
