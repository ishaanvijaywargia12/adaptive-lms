package com.lms.scheduler;

import com.lms.module.gamification.entity.Streak;
import com.lms.module.gamification.repository.StreakRepository;
import com.lms.module.gamification.service.LeaderboardService;
import com.lms.tenant.Tenant;
import com.lms.tenant.TenantContext;
import com.lms.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class LmsScheduler {

    private final TenantRepository tenantRepository;
    private final StreakRepository streakRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LeaderboardService leaderboardService;

    // ─── Streak Reset: runs daily at 00:01 ────────────────────────────────────
    @Scheduled(cron = "0 1 0 * * *")
    @Transactional
    public void resetExpiredStreaks() {
        log.info("Running streak reset job");
        List<Tenant> tenants = tenantRepository.findAll();

        for (Tenant tenant : tenants) {
            if (!tenant.isActive()) continue;

            try {
                TenantContext.setCurrentTenant(tenant.getSchemaName());
                LocalDate yesterday = LocalDate.now().minusDays(1);

                List<Streak> expiredStreaks = streakRepository.findByLastActivityDateBefore(yesterday);

                for (Streak streak : expiredStreaks) {
                    if (streak.getCurrentStreak() > 0) {
                        log.debug("Resetting streak for student {} in tenant {}",
                                streak.getStudentId(), tenant.getSchemaName());
                        streak.setCurrentStreak(0);
                        streakRepository.save(streak);
                    }
                }

            } finally {
                TenantContext.clear();
            }
        }
        log.info("Streak reset job completed");
    }

    // ─── Leaderboard Persist: runs every hour ────────────────────────────────
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void persistLeaderboards() {
        log.debug("Persisting leaderboard data from Redis to DB");
        tenantRepository.findAll().stream()
                .filter(Tenant::isActive)
                .forEach(tenant -> {
                    try {
                        TenantContext.setCurrentTenant(tenant.getSchemaName());
                        leaderboardService.persistGlobalLeaderboard(tenant.getSchemaName());
                    } catch (Exception e) {
                        log.warn("Failed to persist leaderboard for tenant {}: {}", tenant.getSchemaName(), e.getMessage());
                    } finally {
                        TenantContext.clear();
                    }
                });
        log.info("Leaderboard persistence job completed");
    }

    // ─── Weekly Leaderboard Reset: every Monday at midnight ──────────────────
    @Scheduled(cron = "0 0 0 * * MON")
    public void resetWeeklyLeaderboard() {
        log.info("Resetting weekly leaderboards across all active tenants");
        tenantRepository.findAll().stream().filter(Tenant::isActive).forEach(tenant -> {
            try {
                // Delete both global and course-scoped weekly leaderboards
                String pattern = "leaderboard:" + tenant.getSchemaName() + ":*:WEEKLY";
                Set<String> keys = redisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                    log.info("Deleted {} weekly leaderboard keys for tenant {}", keys.size(), tenant.getSchemaName());
                }
            } catch (Exception e) {
                log.warn("Failed to reset weekly leaderboards for tenant {}: {}", tenant.getSchemaName(), e.getMessage());
            }
        });
        log.info("Weekly leaderboard reset complete");
    }
}
