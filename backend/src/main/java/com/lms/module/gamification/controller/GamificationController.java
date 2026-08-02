package com.lms.module.gamification.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.gamification.dto.LeaderboardEntryDto;
import com.lms.module.gamification.entity.StudentBadge;
import com.lms.module.gamification.entity.Streak;
import com.lms.module.gamification.service.GamificationService;
import com.lms.module.gamification.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.lms.security.CurrentUserService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Gamification", description = "Points, badges, streaks, leaderboard")
public class GamificationController {

    private final CurrentUserService currentUserService;

    private final GamificationService gamificationService;
    private final LeaderboardService leaderboardService;

    // ─── Points, Streaks, Badges ──────────────────────────────────────────────

    @GetMapping("/my/points")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my total points")
    public ResponseEntity<ApiResponse<Long>> getMyPoints(@AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success(gamificationService.getTotalPoints(studentId)));
    }

    @GetMapping("/my/streak")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my streak")
    public ResponseEntity<ApiResponse<Streak>> getMyStreak(@AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success(gamificationService.getStreak(studentId)));
    }

    @GetMapping("/my/badges")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my earned badges")
    public ResponseEntity<ApiResponse<List<StudentBadge>>> getMyBadges(@AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success(gamificationService.getStudentBadges(studentId)));
    }

    // ─── Leaderboard ──────────────────────────────────────────────────────────

    /**
     * GET /api/leaderboard?period=ALL_TIME|WEEKLY&topN=50
     */
    @GetMapping("/leaderboard")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get global leaderboard — supports ?period=ALL_TIME|WEEKLY")
    public ResponseEntity<ApiResponse<List<LeaderboardEntryDto>>> getLeaderboard(
            @RequestParam(defaultValue = "ALL_TIME") String period,
            @RequestParam(defaultValue = "50") int topN) {
        List<LeaderboardEntryDto> entries = "WEEKLY".equalsIgnoreCase(period)
                ? leaderboardService.getWeeklyLeaderboard(topN)
                : leaderboardService.getGlobalLeaderboard(topN);
        return ResponseEntity.ok(ApiResponse.success(entries));
    }

    /**
     * GET /api/courses/{courseId}/leaderboard?period=ALL_TIME|WEEKLY&topN=20
     */
    @GetMapping("/courses/{courseId}/leaderboard")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get course-specific leaderboard")
    public ResponseEntity<ApiResponse<List<LeaderboardEntryDto>>> getCourseLeaderboard(
            @PathVariable UUID courseId,
            @RequestParam(defaultValue = "ALL_TIME") String period,
            @RequestParam(defaultValue = "20") int topN) {
        return ResponseEntity.ok(ApiResponse.success(
                leaderboardService.getCourseLeaderboard(courseId, period, topN)));
    }

    @GetMapping("/leaderboard/my-rank")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my rank on the leaderboard")
    public ResponseEntity<ApiResponse<LeaderboardEntryDto>> getMyRank(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "ALL_TIME") String period) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success(
                leaderboardService.getMyRank(studentId, null, period)));
    }
}
