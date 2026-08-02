package com.lms.module.gamification.service;

import com.lms.kafka.event.BaseEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.gamification.entity.*;
import com.lms.module.gamification.repository.*;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationService {

    private final PointTransactionRepository pointTransactionRepository;
    private final StreakRepository streakRepository;
    private final BadgeRepository badgeRepository;
    private final StudentBadgeRepository studentBadgeRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaProducerService kafkaProducer;

    // ─── Point values ─────────────────────────────────────────────────────────
    public static final int PTS_LESSON_COMPLETE = 10;
    public static final int PTS_QUIZ_PASS       = 20;
    public static final int PTS_QUIZ_ACE_BONUS  = 10;   // additional for score > 90%; total ace = 30
    public static final int PTS_ASSIGNMENT_HIGH = 15;   // for score > 80%
    public static final int PTS_STREAK_BONUS    = 5;
    public static final int PTS_COURSE_COMPLETE = 100;

    /**
     * Central point awarding method. Idempotent via {@code idempotencyKey}.
     *
     * <p>Recursion guard: this method does NOT call {@link #recordStreakActivity} —
     * streak is updated by a separate explicit call from event consumers.
     *
     * @param studentId      the student receiving points
     * @param type           event type string (e.g. "LESSON_COMPLETE")
     * @param referenceId    nullable reference (lessonId, quizId, etc.)
     * @param points         points to award (positive)
     * @param idempotencyKey unique key to prevent duplicate awards; null = no dedup
     */
    @Transactional
    public void award(UUID studentId, String type, UUID referenceId, int points, String idempotencyKey) {
        if (idempotencyKey != null && pointTransactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.debug("[GAMIFICATION] Duplicate award skipped. idempotencyKey={}", idempotencyKey);
            return;
        }

        try {
            PointTransaction tx = PointTransaction.builder()
                    .studentId(studentId)
                    .type(type)
                    .referenceId(referenceId)
                    .points(points)
                    .idempotencyKey(idempotencyKey)
                    .build();
            pointTransactionRepository.save(tx);
        } catch (DataIntegrityViolationException ex) {
            // Race condition: another thread inserted with same idempotency key — safe to ignore
            log.debug("[GAMIFICATION] Concurrent duplicate award ignored. idempotencyKey={}", idempotencyKey);
            return;
        }

        // Update Redis leaderboard (global + weekly)
        String tenant = TenantContext.getCurrentTenant();
        updateLeaderboard(tenant, studentId, null, points);

        // Evaluate badges (non-recursive — doesn't award points itself)
        evaluateBadges(studentId);

        log.info("[GAMIFICATION] Awarded {} pts to student={} type={}", points, studentId, type);
    }

    /**
     * Convenience overload without idempotency key (for non-deduped events).
     */
    @Transactional
    public void award(UUID studentId, String type, UUID referenceId, int points) {
        award(studentId, type, referenceId, points, null);
    }

    /**
     * Records daily activity and extends/resets the streak.
     * Awards a streak bonus ONLY when the streak increments (yesterday → today).
     *
     * <p>This is intentionally SEPARATE from {@link #award} to prevent the recursion
     * that existed when award() called updateStreak() which called award().
     */
    @Transactional
    public void recordStreakActivity(UUID studentId) {
        Streak streak = streakRepository.findByStudentId(studentId)
                .orElse(Streak.builder().studentId(studentId).currentStreak(0).longestStreak(0).build());

        LocalDate today = LocalDate.now();
        LocalDate lastActivity = streak.getLastActivityDate();

        if (today.equals(lastActivity)) {
            // Same day — no streak change
            return;
        }

        boolean isConsecutive = lastActivity != null && lastActivity.equals(today.minusDays(1));

        if (isConsecutive) {
            // Yesterday → today: extend streak
            streak.setCurrentStreak(streak.getCurrentStreak() + 1);

            // Award streak bonus (with idempotency key per student per day)
            String idemKey = "STREAK_BONUS:" + studentId + ":" + today;
            award(studentId, "STREAK_BONUS", null, PTS_STREAK_BONUS, idemKey);
        } else {
            // Gap of 2+ days or first activity: start fresh streak
            streak.setCurrentStreak(1);
        }

        if (streak.getCurrentStreak() > streak.getLongestStreak()) {
            streak.setLongestStreak(streak.getCurrentStreak());
        }

        streak.setLastActivityDate(today);
        streakRepository.save(streak);
    }

    /**
     * Evaluate and award badges based on student's current stats.
     * Does NOT award point transactions — badge grants are separate events.
     */
    @Transactional
    public void evaluateBadges(UUID studentId) {
        List<Badge> allBadges = badgeRepository.findAll();
        List<UUID> earnedBadgeIds = studentBadgeRepository.findBadgeIdsByStudentId(studentId);
        Streak streak = streakRepository.findByStudentId(studentId).orElse(null);

        for (Badge badge : allBadges) {
            if (earnedBadgeIds.contains(badge.getId())) continue;

            boolean earned = switch (badge.getCriteriaType()) {
                case "FIRST_ENROLLMENT" -> pointTransactionRepository
                        .existsByStudentIdAndType(studentId, "ENROLLMENT");
                case "QUIZ_ACE" -> pointTransactionRepository
                        .existsByStudentIdAndType(studentId, "QUIZ_ACE_BONUS");
                case "STREAK_7"  -> streak != null && streak.getLongestStreak() >= 7;
                case "STREAK_30" -> streak != null && streak.getLongestStreak() >= 30;
                case "COURSE_COMPLETE" -> pointTransactionRepository
                        .existsByStudentIdAndType(studentId, "COURSE_COMPLETE");
                case "SPEED_LEARNER" -> pointTransactionRepository
                        .existsByStudentIdAndType(studentId, "SPEED_LEARNER");
                default -> false;
            };

            if (earned) {
                StudentBadge sb = StudentBadge.builder()
                        .studentId(studentId)
                        .badgeId(badge.getId())
                        .earnedAt(LocalDateTime.now())
                        .build();
                studentBadgeRepository.save(sb);

                String tenant = TenantContext.getCurrentTenant();
                kafkaProducer.publishBadgeEarned(new BaseEvent.BadgeEarnedEvent(
                        UUID.randomUUID().toString(), tenant, LocalDateTime.now(),
                        studentId, badge.getId(), badge.getName()
                ));

                log.info("[GAMIFICATION] Badge '{}' awarded to student {}", badge.getName(), studentId);
            }
        }
    }

    // ─── Course leaderboard award (used by quiz/lesson event consumers) ────────

    /**
     * Awards points and updates course-scoped leaderboard.
     * Called explicitly by event consumers when a course context is known.
     */
    @Transactional
    public void awardWithCourse(UUID studentId, String type, UUID referenceId,
                                int points, UUID courseId, String idempotencyKey) {
        award(studentId, type, referenceId, points, idempotencyKey);
        // Also update course-scoped leaderboard
        String tenant = TenantContext.getCurrentTenant();
        updateLeaderboard(tenant, studentId, courseId, points);
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    public long getTotalPoints(UUID studentId) {
        return pointTransactionRepository.sumPointsByStudentId(studentId);
    }

    public Streak getStreak(UUID studentId) {
        return streakRepository.findByStudentId(studentId)
                .orElse(Streak.builder().studentId(studentId).currentStreak(0).longestStreak(0).build());
    }

    public List<StudentBadge> getStudentBadges(UUID studentId) {
        return studentBadgeRepository.findByStudentId(studentId);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void updateLeaderboard(String tenant, UUID studentId, UUID courseId, int points) {
        // ALL_TIME global
        String allTimeKey = courseId != null
                ? "leaderboard:" + tenant + ":" + courseId + ":ALL_TIME"
                : "leaderboard:" + tenant + ":global:ALL_TIME";
        redisTemplate.opsForZSet().incrementScore(allTimeKey, studentId.toString(), points);

        // WEEKLY global
        String weeklyKey = courseId != null
                ? "leaderboard:" + tenant + ":" + courseId + ":WEEKLY"
                : "leaderboard:" + tenant + ":global:WEEKLY";
        redisTemplate.opsForZSet().incrementScore(weeklyKey, studentId.toString(), points);
    }
}
