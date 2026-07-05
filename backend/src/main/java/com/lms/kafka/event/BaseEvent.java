package com.lms.kafka.event;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base event payload included in all Kafka messages.
 * tenantId is critical for consumer-side tenant context resolution.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BaseEvent {
    private String eventId;
    private String tenantId;
    private LocalDateTime occurredAt;

    public record LessonCompletedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID studentId, UUID lessonId, UUID courseId, UUID enrollmentId
    ) {}

    public record QuizPassedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID studentId, UUID quizId, UUID attemptId,
            double score, UUID courseId
    ) {}

    public record QuizFailedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID studentId, UUID quizId, UUID attemptId,
            double score
    ) {}

    public record AssignmentSubmittedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID submissionId, UUID assignmentId, UUID studentId, UUID courseId
    ) {}

    public record AssignmentGradedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID submissionId, UUID studentId, UUID instructorId,
            double score, UUID courseId
    ) {}

    public record CourseCompletedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID studentId, UUID courseId, UUID enrollmentId
    ) {}

    public record CertificateIssuedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID certificateId, UUID studentId, UUID courseId,
            String certificateUrl, String verificationCode
    ) {}

    public record BadgeEarnedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID studentId, UUID badgeId, String badgeName
    ) {}

    public record LiveSessionStartedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID sessionId, UUID courseId, UUID instructorId, String title, UUID roomId
    ) {}

    public record LiveSessionEndedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID sessionId, UUID courseId
    ) {}

    public record EnrollmentConfirmedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID enrollmentId, UUID studentId, UUID courseId
    ) {}

    public record CoursePublishedEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID courseId, String title, String description, String[] tags
    ) {}

    public record NotificationSendEvent(
            String eventId, String tenantId, LocalDateTime occurredAt,
            UUID userId, String title, String message, String type, String metadata
    ) {}
}
