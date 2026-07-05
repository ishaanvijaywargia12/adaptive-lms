package com.lms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;

@Configuration
public class KafkaConfig {

    @Bean public NewTopic lessonCompleted() { return topic("lms.lesson.completed", 3); }
    @Bean public NewTopic quizPassed()       { return topic("lms.quiz.passed", 3); }
    @Bean public NewTopic quizFailed()       { return topic("lms.quiz.failed", 3); }
    @Bean public NewTopic assignmentSubmitted() { return topic("lms.assignment.submitted", 3); }
    @Bean public NewTopic assignmentGraded() { return topic("lms.assignment.graded", 3); }
    @Bean public NewTopic courseCompleted()  { return topic("lms.course.completed", 3); }
    @Bean public NewTopic certificateIssued(){ return topic("lms.certificate.issued", 1); }
    @Bean public NewTopic badgeEarned()      { return topic("lms.badge.earned", 1); }
    @Bean public NewTopic liveSessionStarted(){ return topic("lms.live.session.started", 1); }
    @Bean public NewTopic liveSessionEnded() { return topic("lms.live.session.ended", 1); }
    @Bean public NewTopic enrollmentConfirmed(){ return topic("lms.enrollment.confirmed", 3); }
    @Bean public NewTopic coursePublished()  { return topic("lms.course.published", 1); }
    @Bean public NewTopic notificationSend() { return topic("lms.notification.send", 3); }

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
