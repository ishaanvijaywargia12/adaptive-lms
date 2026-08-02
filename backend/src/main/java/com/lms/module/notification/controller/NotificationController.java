package com.lms.module.notification.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.notification.entity.Notification;
import com.lms.module.notification.service.NotificationService;
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
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications")
public class NotificationController {

    private final CurrentUserService currentUserService;

    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all notifications")
    public ResponseEntity<ApiResponse<List<Notification>>> getAll(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success(notificationService.getAll(userId)));
    }

    @GetMapping("/unread")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get unread notifications")
    public ResponseEntity<ApiResponse<List<Notification>>> getUnread(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success(notificationService.getUnread(userId)));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserService.resolveOrProvision(jwt).getId();
        notificationService.markRead(id, userId);
        return ResponseEntity.ok(ApiResponse.success("Marked as read", null));
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Void>> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllRead(currentUserService.resolveOrProvision(jwt).getId());
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }
}
