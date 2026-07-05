package com.lms.kafka.consumer;

import com.lms.kafka.event.BaseEvent;
import com.lms.module.certificate.service.CertificateService;
import com.lms.module.gamification.service.GamificationService;
import com.lms.module.notification.service.NotificationService;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * All Kafka event consumers.
 * Every consumer:
 * 1. Sets tenant context from event payload
 * 2. Delegates to appropriate service
 * 3. Relies on service-layer idempotency (EventIdempotencyService)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LmsEventConsumer {

    private final GamificationService gamificationService;
    private final CertificateService certificateService;
    private final NotificationService notificationService;
    private final EventIdempotencyService idempotencyService;

    @KafkaListener(topics = "lms.lesson.completed", groupId = "lms-consumer-group")
    public void onLessonCompleted(BaseEvent.LessonCompletedEvent event) {
        if (idempotencyService.isProcessed(event.eventId())) return;
        try {
            TenantContext.setCurrentTenant(event.tenantId());
            log.info("Lesson completed: student={} lesson={}", event.studentId(), event.lessonId());
            // Award points
            gamificationService.award(event.studentId(), "LESSON_COMPLETE", event.lessonId(),
                    GamificationService.PTS_LESSON_COMPLETE);
            // Send notification
            notificationService.send(event.studentId(), "Lesson Completed! 🎯",
                    "You completed a lesson and earned 10 points.", "LESSON_COMPLETE");
            idempotencyService.markProcessed(event.eventId(), "lms.lesson.completed");
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = "lms.quiz.passed", groupId = "lms-consumer-group")
    public void onQuizPassed(BaseEvent.QuizPassedEvent event) {
        if (idempotencyService.isProcessed(event.eventId())) return;
        try {
            TenantContext.setCurrentTenant(event.tenantId());

            // Fix: award total points in a single transaction (pass + optional ace bonus)
            // This prevents QUIZ_ACE_BONUS from being double-counted via two separate awards
            int totalPoints = GamificationService.PTS_QUIZ_PASS;
            String type = "QUIZ_PASS";
            boolean isAce = event.score() >= 90.0;

            if (isAce) {
                totalPoints += GamificationService.PTS_QUIZ_ACE_BONUS;
                type = "QUIZ_ACE_BONUS"; // Use ace type so badge evaluation works
            }

            gamificationService.award(event.studentId(), type, event.quizId(), totalPoints);

            String message = isAce
                    ? String.format("Quiz Ace! 🏆 Scored %.0f%% and earned %d points (including ace bonus).", event.score(), totalPoints)
                    : String.format("Quiz Passed! 🎉 Scored %.0f%% and earned %d points.", event.score(), totalPoints);

            notificationService.send(event.studentId(), isAce ? "Quiz Ace! 🏆" : "Quiz Passed! 🎉", message, "QUIZ_PASSED");
            idempotencyService.markProcessed(event.eventId(), "lms.quiz.passed");
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = "lms.course.completed", groupId = "lms-consumer-group")
    public void onCourseCompleted(BaseEvent.CourseCompletedEvent event) {
        if (idempotencyService.isProcessed(event.eventId())) return;
        try {
            TenantContext.setCurrentTenant(event.tenantId());
            // Award points
            gamificationService.award(event.studentId(), "COURSE_COMPLETE", event.courseId(),
                    GamificationService.PTS_COURSE_COMPLETE);
            // Generate certificate
            certificateService.generateCertificate(event.studentId(), event.courseId(), event.tenantId());
            notificationService.send(event.studentId(), "🎓 Course Completed!",
                    "Congratulations! Your certificate is being generated.", "COURSE_COMPLETE");
            idempotencyService.markProcessed(event.eventId(), "lms.course.completed");
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = "lms.assignment.submitted", groupId = "lms-consumer-group")
    public void onAssignmentSubmitted(BaseEvent.AssignmentSubmittedEvent event) {
        if (idempotencyService.isProcessed(event.eventId())) return;
        try {
            TenantContext.setCurrentTenant(event.tenantId());
            // Plagiarism check is triggered asynchronously
            log.info("Assignment submitted: submission={}", event.submissionId());
            notificationService.send(event.studentId(), "Submission Received 📝",
                    "Your assignment submission is being processed.", "ASSIGNMENT_SUBMITTED");
            idempotencyService.markProcessed(event.eventId(), "lms.assignment.submitted");
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = "lms.assignment.graded", groupId = "lms-consumer-group")
    public void onAssignmentGraded(BaseEvent.AssignmentGradedEvent event) {
        if (idempotencyService.isProcessed(event.eventId())) return;
        try {
            TenantContext.setCurrentTenant(event.tenantId());
            if (event.score() >= 80.0) {
                gamificationService.award(event.studentId(), "ASSIGNMENT_HIGH_SCORE",
                        event.submissionId(), GamificationService.PTS_ASSIGNMENT_HIGH);
            }
            notificationService.send(event.studentId(), "Assignment Graded ✅",
                    String.format("Your assignment has been graded: %.0f/100", event.score()),
                    "ASSIGNMENT_GRADED");
            idempotencyService.markProcessed(event.eventId(), "lms.assignment.graded");
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = "lms.badge.earned", groupId = "lms-consumer-group")
    public void onBadgeEarned(BaseEvent.BadgeEarnedEvent event) {
        if (idempotencyService.isProcessed(event.eventId())) return;
        try {
            TenantContext.setCurrentTenant(event.tenantId());
            notificationService.send(event.studentId(), "New Badge Earned! 🏅",
                    "You earned the '" + event.badgeName() + "' badge!", "BADGE_EARNED");
            idempotencyService.markProcessed(event.eventId(), "lms.badge.earned");
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = "lms.live.session.started", groupId = "lms-consumer-group")
    public void onLiveSessionStarted(BaseEvent.LiveSessionStartedEvent event) {
        if (idempotencyService.isProcessed(event.eventId())) return;
        try {
            TenantContext.setCurrentTenant(event.tenantId());
            log.info("Live session started: session={} course={}", event.sessionId(), event.courseId());
            idempotencyService.markProcessed(event.eventId(), "lms.live.session.started");
        } finally {
            TenantContext.clear();
        }
    }

    @KafkaListener(topics = "lms.enrollment.confirmed", groupId = "lms-consumer-group")
    public void onEnrollmentConfirmed(BaseEvent.EnrollmentConfirmedEvent event) {
        if (idempotencyService.isProcessed(event.eventId())) return;
        try {
            TenantContext.setCurrentTenant(event.tenantId());
            gamificationService.award(event.studentId(), "ENROLLMENT", event.courseId(), 0);
            notificationService.send(event.studentId(), "Enrollment Confirmed! 🎉",
                    "You are now enrolled in your new course.", "ENROLLMENT_CONFIRMED");
            idempotencyService.markProcessed(event.eventId(), "lms.enrollment.confirmed");
        } finally {
            TenantContext.clear();
        }
    }
}
