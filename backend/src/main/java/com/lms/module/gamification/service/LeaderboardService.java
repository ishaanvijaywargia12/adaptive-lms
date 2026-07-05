package com.lms.module.gamification.service;

import com.lms.module.gamification.dto.LeaderboardEntryDto;
import com.lms.module.gamification.entity.LeaderboardEntry;
import com.lms.module.gamification.repository.LeaderboardEntryRepository;
import com.lms.module.gamification.repository.PointTransactionRepository;
import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LeaderboardService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PointTransactionRepository pointTransactionRepository;
    private final LeaderboardEntryRepository leaderboardEntryRepository;
    private final UserRepository userRepository;

    // ─── Global Leaderboard ───────────────────────────────────────────────────

    public List<LeaderboardEntryDto> getGlobalLeaderboard(int topN) {
        return getLeaderboard(null, "ALL_TIME", topN);
    }

    public List<LeaderboardEntryDto> getWeeklyLeaderboard(int topN) {
        return getLeaderboard(null, "WEEKLY", topN);
    }

    public List<LeaderboardEntryDto> getCourseLeaderboard(UUID courseId, String period, int topN) {
        return getLeaderboard(courseId, period, topN);
    }

    /**
     * Unified leaderboard fetch — tries Redis first, falls back to DB.
     */
    private List<LeaderboardEntryDto> getLeaderboard(UUID courseId, String period, int topN) {
        try {
            String tenant = TenantContext.getCurrentTenant();
            String key = buildRedisKey(tenant, courseId, period);

            Set<ZSetOperations.TypedTuple<Object>> entries =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, topN - 1);

            if (entries != null && !entries.isEmpty()) {
                return buildDtosFromRedis(entries, period, courseId);
            }
        } catch (Exception e) {
            log.warn("Redis leaderboard unavailable (period={} courseId={}), falling back to DB: {}",
                    period, courseId, e.getMessage());
        }

        // DB fallback — only available for ALL_TIME
        if ("ALL_TIME".equals(period)) {
            return buildDtosFromDb(topN, period, courseId);
        }
        return List.of(); // Weekly with no Redis data returns empty
    }

    // ─── My Rank ──────────────────────────────────────────────────────────────

    public LeaderboardEntryDto getMyRank(UUID studentId) {
        return getMyRank(studentId, null, "ALL_TIME");
    }

    public LeaderboardEntryDto getMyRank(UUID studentId, UUID courseId, String period) {
        try {
            String tenant = TenantContext.getCurrentTenant();
            String key = buildRedisKey(tenant, courseId, period);

            Double score = redisTemplate.opsForZSet().score(key, studentId.toString());
            if (score != null) {
                Long reverseRank = redisTemplate.opsForZSet().reverseRank(key, studentId.toString());
                User user = userRepository.findById(studentId).orElse(null);
                return LeaderboardEntryDto.builder()
                        .studentId(studentId)
                        .studentName(user != null ? (user.getFirstName() + " " + user.getLastName()).trim() : "Learner")
                        .avatarUrl(user != null ? user.getAvatarUrl() : null)
                        .totalPoints(score.longValue())
                        .rank(reverseRank != null ? (int) (reverseRank + 1) : -1)
                        .period(period)
                        .scope(courseId != null ? courseId.toString() : "GLOBAL")
                        .build();
            }
        } catch (Exception e) {
            log.warn("Redis rank lookup failed, falling back to DB: {}", e.getMessage());
        }

        long totalPoints = pointTransactionRepository.sumPointsByStudentId(studentId);
        String name = resolveDisplayName(studentId);
        return LeaderboardEntryDto.builder()
                .studentId(studentId)
                .studentName(name)
                .totalPoints(totalPoints)
                .rank(-1)
                .period(period)
                .scope(courseId != null ? courseId.toString() : "GLOBAL")
                .build();
    }

    // ─── Persistence: Redis → DB ──────────────────────────────────────────────

    /**
     * Called by the hourly scheduler to persist Redis leaderboard entries to DB.
     * Only persists ALL_TIME global leaderboard.
     */
    @Transactional
    public void persistGlobalLeaderboard(String tenant) {
        String key = buildRedisKey(tenant, null, "ALL_TIME");
        Set<ZSetOperations.TypedTuple<Object>> entries =
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);

        if (entries == null || entries.isEmpty()) {
            log.debug("No Redis leaderboard entries for tenant {} to persist", tenant);
            return;
        }

        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> entry : entries) {
            try {
                UUID studentId = UUID.fromString((String) entry.getValue());
                long points = entry.getScore() != null ? entry.getScore().longValue() : 0L;

                Optional<LeaderboardEntry> existing =
                        leaderboardEntryRepository.findByStudentIdAndCourseIdAndPeriod(
                                studentId, null, LeaderboardEntry.Period.ALL_TIME);

                LeaderboardEntry dbEntry = existing.orElse(
                        LeaderboardEntry.builder()
                                .studentId(studentId)
                                .courseId(null)
                                .period(LeaderboardEntry.Period.ALL_TIME)
                                .build()
                );

                dbEntry.setTotalPoints(points);
                dbEntry.setRank(rank++);
                leaderboardEntryRepository.save(dbEntry);
            } catch (Exception e) {
                log.warn("Failed to persist leaderboard entry: {}", e.getMessage());
            }
        }
        log.info("Persisted {} leaderboard entries for tenant {}", entries.size(), tenant);
    }

    // ─── Private Helpers ──────────────────────────────────────────────────────

    private String buildRedisKey(String tenant, UUID courseId, String period) {
        return courseId != null
                ? "leaderboard:" + tenant + ":" + courseId + ":" + period
                : "leaderboard:" + tenant + ":global:" + period;
    }

    private List<LeaderboardEntryDto> buildDtosFromRedis(
            Set<ZSetOperations.TypedTuple<Object>> entries, String period, UUID courseId) {
        List<UUID> studentIds = entries.stream()
                .map(e -> UUID.fromString((String) e.getValue()))
                .collect(Collectors.toList());

        Map<UUID, User> userMap = buildUserMap(studentIds);

        List<LeaderboardEntryDto> result = new ArrayList<>();
        int rank = 1;
        for (ZSetOperations.TypedTuple<Object> entry : entries) {
            UUID studentId = UUID.fromString((String) entry.getValue());
            User user = userMap.get(studentId);
            result.add(LeaderboardEntryDto.builder()
                    .studentId(studentId)
                    .studentName(user != null ? (user.getFirstName() + " " + user.getLastName()).trim() : "Learner")
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .totalPoints(entry.getScore() != null ? entry.getScore().longValue() : 0)
                    .rank(rank++)
                    .period(period)
                    .scope(courseId != null ? courseId.toString() : "GLOBAL")
                    .build());
        }
        return result;
    }

    private List<LeaderboardEntryDto> buildDtosFromDb(int topN, String period, UUID courseId) {
        List<Object[]> rows = pointTransactionRepository.findTopStudentsByPoints(topN);
        List<UUID> studentIds = rows.stream().map(r -> (UUID) r[0]).collect(Collectors.toList());
        Map<UUID, User> userMap = buildUserMap(studentIds);

        List<LeaderboardEntryDto> result = new ArrayList<>();
        int rank = 1;
        for (Object[] row : rows) {
            UUID studentId = (UUID) row[0];
            long points = ((Number) row[1]).longValue();
            User user = userMap.get(studentId);
            result.add(LeaderboardEntryDto.builder()
                    .studentId(studentId)
                    .studentName(user != null ? (user.getFirstName() + " " + user.getLastName()).trim() : "Learner")
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .totalPoints(points)
                    .rank(rank++)
                    .period(period)
                    .scope(courseId != null ? courseId.toString() : "GLOBAL")
                    .build());
        }
        return result;
    }

    private Map<UUID, User> buildUserMap(List<UUID> studentIds) {
        if (studentIds.isEmpty()) return Map.of();
        Map<UUID, User> map = new HashMap<>();
        for (User u : userRepository.findAllById(studentIds)) {
            map.put(u.getId(), u);
        }
        return map;
    }

    private String resolveDisplayName(UUID studentId) {
        return userRepository.findById(studentId)
                .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                .orElse("Learner");
    }
}
