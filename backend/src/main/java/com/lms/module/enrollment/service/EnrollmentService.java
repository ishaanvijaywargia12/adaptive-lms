package com.lms.module.enrollment.service;

import com.lms.common.exception.BusinessLogicException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.kafka.event.BaseEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.enrollment.entity.Enrollment;
import com.lms.module.enrollment.repository.EnrollmentRepository;
import com.lms.module.lesson.entity.Lesson;
import com.lms.module.lesson.entity.LessonProgress;
import com.lms.module.lesson.repository.LessonProgressRepository;
import com.lms.module.lesson.repository.LessonRepository;
import com.lms.module.module.repository.CourseModuleRepository;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final CourseModuleRepository moduleRepository;
    private final KafkaProducerService kafkaProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public Enrollment enroll(UUID studentId, UUID courseId) {
        if (enrollmentRepository.existsByStudentIdAndCourseId(studentId, courseId)) {
            throw new BusinessLogicException("Student is already enrolled in this course");
        }

        Enrollment enrollment = Enrollment.builder()
                .studentId(studentId)
                .courseId(courseId)
                .enrolledAt(LocalDateTime.now())
                .progressPercent(BigDecimal.ZERO)
                .build();

        enrollment = enrollmentRepository.save(enrollment);

        String tenantId = TenantContext.getCurrentTenant();
        kafkaProducer.publishEnrollmentConfirmed(new BaseEvent.EnrollmentConfirmedEvent(
                UUID.randomUUID().toString(), tenantId, LocalDateTime.now(),
                enrollment.getId(), studentId, courseId
        ));

        log.info("Student {} enrolled in course {}", studentId, courseId);
        return enrollment;
    }

    @Transactional
    public void completeLesson(UUID studentId, UUID lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId.toString()));

        // Find enrollment via module → course
        UUID courseId = moduleRepository.findCourseIdByModuleId(lesson.getModuleId())
                .orElseThrow(() -> new ResourceNotFoundException("Course for lesson", lessonId.toString()));

        Enrollment enrollment = enrollmentRepository
                .findByStudentIdAndCourseId(studentId, courseId)
                .orElseThrow(() -> new BusinessLogicException("Not enrolled in this course"));

        // Dedup: if lesson already completed for this enrollment, skip point awarding
        boolean alreadyCompleted = lessonProgressRepository
                .existsByEnrollmentIdAndLessonId(enrollment.getId(), lessonId);

        if (!alreadyCompleted) {
            // Record lesson completion
            lessonProgressRepository.save(LessonProgress.builder()
                    .enrollmentId(enrollment.getId())
                    .lessonId(lessonId)
                    .completedAt(LocalDateTime.now())
                    .build());
        }

        // Always recalculate progress (idempotent)
        BigDecimal progress = recalculateProgress(enrollment.getId(), courseId);
        enrollment.setProgressPercent(progress);
        enrollment.setLastAccessedAt(LocalDateTime.now());

        // Cache progress in Redis
        String cacheKey = "progress:" + TenantContext.getCurrentTenant() + ":" + studentId + ":" + courseId;
        redisTemplate.opsForValue().set(cacheKey, progress.toPlainString(), 1, TimeUnit.HOURS);

        String tenantId = TenantContext.getCurrentTenant();

        // Check if course completed (only fires the event once via completedAt guard)
        if (progress.compareTo(BigDecimal.valueOf(100)) >= 0 && enrollment.getCompletedAt() == null) {
            enrollment.setCompletedAt(LocalDateTime.now());
            enrollmentRepository.save(enrollment);

            kafkaProducer.publishCourseCompleted(new BaseEvent.CourseCompletedEvent(
                    UUID.randomUUID().toString(), tenantId, LocalDateTime.now(),
                    studentId, courseId, enrollment.getId()
            ));
            log.info("Student {} completed course {} — publishing completion event", studentId, courseId);
        } else {
            enrollmentRepository.save(enrollment);
        }

        // Publish lesson completed event (only if not already completed — prevents duplicate points)
        if (!alreadyCompleted) {
            kafkaProducer.publishLessonCompleted(new BaseEvent.LessonCompletedEvent(
                    UUID.randomUUID().toString(), tenantId, LocalDateTime.now(),
                    studentId, lessonId, courseId, enrollment.getId()
            ));
            log.debug("Lesson {} completed for student {} (progress: {}%)", lessonId, studentId, progress);
        } else {
            log.debug("Lesson {} already completed for student {}, skipping point award", lessonId, studentId);
        }
    }

    private BigDecimal recalculateProgress(UUID enrollmentId, UUID courseId) {
        long totalLessons = lessonRepository.countByCourseId(courseId);
        if (totalLessons == 0) return BigDecimal.ZERO;

        long completedLessons = lessonProgressRepository.countByEnrollmentId(enrollmentId);
        return BigDecimal.valueOf(completedLessons * 100.0 / totalLessons)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public List<Enrollment> getMyEnrollments(UUID studentId) {
        return enrollmentRepository.findByStudentId(studentId);
    }
}
