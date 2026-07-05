package com.lms.kafka.producer;

import com.lms.kafka.event.BaseEvent;
import com.lms.kafka.event.RagDocumentIngestionEvent;
import com.lms.kafka.event.RagDoubtSubmittedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String LESSON_COMPLETED = "lms.lesson.completed";
    private static final String QUIZ_PASSED = "lms.quiz.passed";
    private static final String QUIZ_FAILED = "lms.quiz.failed";
    private static final String ASSIGNMENT_SUBMITTED = "lms.assignment.submitted";
    private static final String ASSIGNMENT_GRADED = "lms.assignment.graded";
    private static final String COURSE_COMPLETED = "lms.course.completed";
    private static final String CERTIFICATE_ISSUED = "lms.certificate.issued";
    private static final String BADGE_EARNED = "lms.badge.earned";
    private static final String LIVE_STARTED = "lms.live.session.started";
    private static final String LIVE_ENDED = "lms.live.session.ended";
    private static final String ENROLLMENT_CONFIRMED = "lms.enrollment.confirmed";
    private static final String COURSE_PUBLISHED = "lms.course.published";
    private static final String NOTIFICATION_SEND = "lms.notification.send";

    // ─── RAG Topic Constants ────────────────────────────────────────────────
    private static final String RAG_DOCUMENT_INGESTION  = "lms.rag.document.ingestion.requested";
    private static final String RAG_DOUBT_SUBMITTED     = "lms.rag.doubt.submitted";
    private static final String RAG_DOCUMENT_INGEST_DLQ = "lms.rag.document.ingestion.dlq";
    private static final String RAG_DOUBT_DLQ           = "lms.rag.doubt.dlq";

    public void publishLessonCompleted(BaseEvent.LessonCompletedEvent event) {
        publish(LESSON_COMPLETED, event.studentId().toString(), event);
    }

    public void publishQuizPassed(BaseEvent.QuizPassedEvent event) {
        publish(QUIZ_PASSED, event.studentId().toString(), event);
    }

    public void publishQuizFailed(BaseEvent.QuizFailedEvent event) {
        publish(QUIZ_FAILED, event.studentId().toString(), event);
    }

    public void publishAssignmentSubmitted(BaseEvent.AssignmentSubmittedEvent event) {
        publish(ASSIGNMENT_SUBMITTED, event.studentId().toString(), event);
    }

    public void publishAssignmentGraded(BaseEvent.AssignmentGradedEvent event) {
        publish(ASSIGNMENT_GRADED, event.studentId().toString(), event);
    }

    public void publishCourseCompleted(BaseEvent.CourseCompletedEvent event) {
        publish(COURSE_COMPLETED, event.studentId().toString(), event);
    }

    public void publishCertificateIssued(BaseEvent.CertificateIssuedEvent event) {
        publish(CERTIFICATE_ISSUED, event.studentId().toString(), event);
    }

    public void publishBadgeEarned(BaseEvent.BadgeEarnedEvent event) {
        publish(BADGE_EARNED, event.studentId().toString(), event);
    }

    public void publishLiveSessionStarted(BaseEvent.LiveSessionStartedEvent event) {
        publish(LIVE_STARTED, event.sessionId().toString(), event);
    }

    public void publishLiveSessionEnded(BaseEvent.LiveSessionEndedEvent event) {
        publish(LIVE_ENDED, event.sessionId().toString(), event);
    }

    public void publishEnrollmentConfirmed(BaseEvent.EnrollmentConfirmedEvent event) {
        publish(ENROLLMENT_CONFIRMED, event.studentId().toString(), event);
    }

    public void publishCoursePublished(BaseEvent.CoursePublishedEvent event) {
        publish(COURSE_PUBLISHED, event.courseId().toString(), event);
    }

    public void publishNotification(BaseEvent.NotificationSendEvent event) {
        publish(NOTIFICATION_SEND, event.userId().toString(), event);
    }

    // ─── RAG Publish Methods ────────────────────────────────────────────────

    /**
     * Publishes a document ingestion event when an instructor uploads a course PDF.
     * Key = courseId (ensures all chunks for a course land in the same partition).
     */
    public void publishDocumentIngestionEvent(RagDocumentIngestionEvent event) {
        publish(RAG_DOCUMENT_INGESTION, event.getCourseId().toString(), event);
    }

    /**
     * Publishes a doubt submission event for async RAG processing.
     * Key = studentId (collocates a student's doubts in the same partition).
     */
    public void publishDoubtSubmittedEvent(RagDoubtSubmittedEvent event) {
        publish(RAG_DOUBT_SUBMITTED, event.getStudentId().toString(), event);
    }

    /**
     * Routes a failed ingestion event to the dead-letter topic for manual replay.
     */
    public void publishIngestionDlq(RagDocumentIngestionEvent event) {
        publish(RAG_DOCUMENT_INGEST_DLQ, event.getCourseId().toString(), event);
    }

    /**
     * Routes a failed doubt event to the dead-letter topic for manual replay.
     */
    public void publishDoubtDlq(RagDoubtSubmittedEvent event) {
        publish(RAG_DOUBT_DLQ, event.getStudentId().toString(), event);
    }

    private void publish(String topic, String key, Object payload) {
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish to topic {}: {}", topic, ex.getMessage());
                    } else {
                        log.debug("Published to topic {} partition {} offset {}",
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
