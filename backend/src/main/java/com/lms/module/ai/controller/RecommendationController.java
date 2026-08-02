package com.lms.module.ai.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.ai.entity.AiRecommendation;
import com.lms.module.ai.service.RecommendationService;
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
@RequestMapping("/api/my/recommendations")
@RequiredArgsConstructor
@Tag(name = "AI Recommendations", description = "AI-driven course recommendation endpoints")
public class RecommendationController {

    private final CurrentUserService currentUserService;

    private final RecommendationService recommendationService;

    @GetMapping
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get course recommendations for the authenticated student")
    public ResponseEntity<ApiResponse<List<AiRecommendation>>> getMyRecommendations(
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        List<AiRecommendation> recommendations = recommendationService.getRecommendations(studentId);
        return ResponseEntity.ok(ApiResponse.success("Recommendations fetched", recommendations));
    }

    @PostMapping("/{recommendationId}/dismiss")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Dismiss a course recommendation")
    public ResponseEntity<ApiResponse<Void>> dismissRecommendation(
            @PathVariable UUID recommendationId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        recommendationService.dismiss(recommendationId, studentId);
        return ResponseEntity.ok(ApiResponse.success("Recommendation dismissed", null));
    }
}
