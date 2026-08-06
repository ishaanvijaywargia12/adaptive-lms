package com.lms.scheduler;

import com.lms.tenant.Tenant;
import com.lms.tenant.TenantContext;
import com.lms.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Scheduled maintenance tasks: streak reset, leaderboard persistence, weekly leaderboard reset.
 *
 * <p><strong>Transaction safety:</strong> Per-tenant operations are delegated to
 * {@link SchedulerTenantService}, a separate Spring bean. This ensures {@code @Transactional(REQUIRES_NEW)}
 * is applied correctly through the AOP proxy (self-invocation within the same class bypasses the proxy).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LmsScheduler {

    private final TenantRepository tenantRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SchedulerTenantService schedulerTenantService;

    // ─── Streak Reset: runs daily at 00:01 ────────────────────────────────────

    @Scheduled(cron = "0 1 0 * * *")
    public void resetExpiredStreaks() {
        log.info("Running streak reset job");
        for (Tenant tenant : tenantRepository.findAll()) {
            if (!tenant.isActive()) continue;
            TenantContext.setCurrentTenant(tenant.getSchemaName());
            try {
                schedulerTenantService.resetStreaksForTenant(tenant.getSchemaName());
            } catch (Exception e) {
                log.warn("Streak reset failed for tenant {}: {}", tenant.getSchemaName(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Streak reset job completed");
    }

    // ─── Leaderboard Persist: runs every hour ────────────────────────────────

    @Scheduled(fixedRate = 3_600_000)
    public void persistLeaderboards() {
        log.debug("Persisting leaderboard data from Redis to DB");
        for (Tenant tenant : tenantRepository.findAll()) {
            if (!tenant.isActive()) continue;
            TenantContext.setCurrentTenant(tenant.getSchemaName());
            try {
                schedulerTenantService.persistLeaderboardForTenant(tenant.getSchemaName());
            } catch (Exception e) {
                log.warn("Leaderboard persist failed for tenant {}: {}", tenant.getSchemaName(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
        log.info("Leaderboard persistence job completed");
    }

    // ─── Weekly Leaderboard Reset: every Monday at midnight ──────────────────

    @Scheduled(cron = "0 0 0 * * MON")
    public void resetWeeklyLeaderboard() {
        log.info("Resetting weekly leaderboards across all active tenants");
        for (Tenant tenant : tenantRepository.findAll()) {
            if (!tenant.isActive()) continue;
            try {
                resetWeeklyLeaderboardForTenant(tenant.getSchemaName());
            } catch (Exception e) {
                log.warn("Weekly leaderboard reset failed for tenant {}: {}", tenant.getSchemaName(), e.getMessage());
            }
        }
        log.info("Weekly leaderboard reset complete");
    }

    /**
     * Uses SCAN instead of KEYS to avoid blocking Redis in production.
     * Pattern: leaderboard:{tenant}:*:WEEKLY
     */
    private void resetWeeklyLeaderboardForTenant(String schemaName) {
        String pattern = "leaderboard:" + schemaName + ":*:WEEKLY";
        List<String> keysToDelete = new ArrayList<>();

        // Use SCAN (non-blocking) instead of KEYS
        ScanOptions scanOptions = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (var cursor = redisTemplate.scan(scanOptions)) {
            while (cursor.hasNext()) {
                keysToDelete.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("SCAN failed for pattern {}: {}", pattern, e.getMessage());
        }

        if (!keysToDelete.isEmpty()) {
            redisTemplate.delete(keysToDelete);
            log.info("Deleted {} weekly leaderboard keys for tenant {}", keysToDelete.size(), schemaName);
        }
    }
}
