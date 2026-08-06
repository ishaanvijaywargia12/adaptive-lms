package com.lms.scheduler;

import com.lms.module.gamification.entity.Streak;
import com.lms.module.gamification.repository.StreakRepository;
import com.lms.module.gamification.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Holds per-tenant scheduler operations that require REQUIRES_NEW transactions.
 *
 * <p>These methods are intentionally in a SEPARATE bean from {@link LmsScheduler}.
 * Spring's AOP proxy only intercepts calls that go through the proxy (i.e., calls from
 * OTHER beans). Self-invocation within the same class bypasses the proxy and silently
 * ignores @Transactional annotations.
 *
 * <p>{@link LmsScheduler} injects this bean and calls through it, so REQUIRES_NEW
 * correctly creates a new transaction for each tenant iteration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerTenantService {

    private final StreakRepository streakRepository;
    private final LeaderboardService leaderboardService;

    /**
     * Resets streaks for a single tenant inside its own new transaction.
     * Caller (LmsScheduler) MUST set TenantContext before calling this method.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetStreaksForTenant(String schemaName) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<Streak> expiredStreaks = streakRepository.findByLastActivityDateBefore(yesterday);
        for (Streak streak : expiredStreaks) {
            if (streak.getCurrentStreak() > 0) {
                log.debug("Resetting streak for student {} in tenant {}", streak.getStudentId(), schemaName);
                streak.setCurrentStreak(0);
                streakRepository.save(streak);
            }
        }
        log.debug("Streak reset complete for tenant {}: {} expired streaks", schemaName, expiredStreaks.size());
    }

    /**
     * Persists leaderboard data for a single tenant inside its own new transaction.
     * Caller (LmsScheduler) MUST set TenantContext before calling this method.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistLeaderboardForTenant(String schemaName) {
        leaderboardService.persistGlobalLeaderboard(schemaName);
    }
}
