package com.lms.module.gamification.service;

import com.lms.kafka.event.BaseEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.gamification.entity.*;
import com.lms.module.gamification.repository.*;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    public static final int PTS_QUIZ_PASS = 20;
    public static final int PTS_QUIZ_ACE_BONUS = 10;   // for score > 90%
    public static final int PTS_ASSIGNMENT_HIGH = 15;   // for score > 80%
    public static final int PTS_STREAK_BONUS = 5;
    public static final int PTS_COURSE_COMPLETE = 100;

    /**
     * Central point awarding method. All point changes go through here.
     */
    @Transactional
    public void award(UUID studentId, String type, UUID referenceId, int points) {
        PointTransaction tx = PointTransaction.builder()
                .studentId(studentId)
                .type(type)
                .referenceId(referenceId)
                .points(points)
                .build();
        pointTransactionRepository.save(tx);

        // Update Redis leaderboards
        String tenant = TenantContext.getCurrentTenant();
        updateLeaderboard(tenant, studentId, null, points);  // global
        // Note: course-specific leaderboard updated from calling service

        // Update streak on activity
        updateStreak(studentId);

        // Evaluate badges
        evaluateBadges(studentId);

        log.info("Awarded {} points to student {} for {}", points, studentId, type);
    }

    /**
     * Update lesson-complete on streaks; streak bonus awarded separately.
     */
    @Transactional
    public void updateStreak(UUID studentId) {
        Streak streak = streakRepository.findByStudentId(studentId)
                .orElse(Streak.builder().studentId(studentId).build());

        LocalDate today = LocalDate.now();
        LocalDate lastActivity = streak.getLastActivityDate();

        if (lastActivity == null || lastActivity.isBefore(today.minusDays(1))) {
            // Gap or first activity — streak may reset (handled by scheduler for midnight check)
            if (lastActivity != null && lastActivity.isBefore(today.minusDays(1))) {
                streak.setCurrentStreak(1);  // reset
            } else if (lastActivity == null) {
                streak.setCurrentStreak(1);
            }
        } else if (lastActivity.isBefore(today)) {
            // Yesterday → extend streak
            streak.setCurrentStreak(streak.getCurrentStreak() + 1);

            // Streak bonus points
            award(studentId, "STREAK_BONUS", null, PTS_STREAK_BONUS);
        }
        // Same day: no change to streak count

        if (streak.getCurrentStreak() > streak.getLongestStreak()) {
            streak.setLongestStreak(streak.getCurrentStreak());
        }

        streak.setLastActivityDate(today);
        streakRepository.save(streak);
    }

    /**
     * Evaluate and award badges based on student's current stats.
     */
    @Transactional
    public void evaluateBadges(UUID studentId) {
        List<Badge> allBadges = badgeRepository.findAll();
        List<UUID> earnedBadgeIds = studentBadgeRepository.findBadgeIdsByStudentId(studentId);

        long totalPoints = pointTransactionRepository.sumPointsByStudentId(studentId);
        Streak streak = streakRepository.findByStudentId(studentId).orElse(null);

        for (Badge badge : allBadges) {
            if (earnedBadgeIds.contains(badge.getId())) continue; // Already earned

            boolean earned = switch (badge.getCriteriaType()) {
                case "FIRST_ENROLLMENT" -> pointTransactionRepository
                        .existsByStudentIdAndType(studentId, "ENROLLMENT");
                case "QUIZ_ACE" -> pointTransactionRepository
                        .existsByStudentIdAndType(studentId, "QUIZ_ACE_BONUS");
                case "STREAK_7" -> streak != null && streak.getLongestStreak() >= 7;
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

                log.info("Badge '{}' awarded to student {}", badge.getName(), studentId);
            }
        }
    }

    public long getTotalPoints(UUID studentId) {
        return pointTransactionRepository.sumPointsByStudentId(studentId);
    }

    private void updateLeaderboard(String tenant, UUID studentId, UUID courseId, int points) {
        String period = "ALL_TIME";
        String key = courseId != null
                ? "leaderboard:" + tenant + ":" + courseId + ":" + period
                : "leaderboard:" + tenant + ":global:" + period;
        redisTemplate.opsForZSet().incrementScore(key, studentId.toString(), points);

        // Weekly leaderboard
        String weeklyKey = courseId != null
                ? "leaderboard:" + tenant + ":" + courseId + ":WEEKLY"
                : "leaderboard:" + tenant + ":global:WEEKLY";
        redisTemplate.opsForZSet().incrementScore(weeklyKey, studentId.toString(), points);
    }

    public Streak getStreak(UUID studentId) {
        return streakRepository.findByStudentId(studentId)
                .orElse(Streak.builder().studentId(studentId).currentStreak(0).longestStreak(0).build());
    }

    public List<StudentBadge> getStudentBadges(UUID studentId) {
        return studentBadgeRepository.findByStudentId(studentId);
    }
}
