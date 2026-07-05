package com.lms.module.live.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.live.entity.LiveSession;
import com.lms.module.live.service.LiveSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Live Sessions", description = "WebRTC live class management and WebSocket signaling")
public class LiveSessionController {

    private final LiveSessionService liveSessionService;
    private final SimpMessagingTemplate messagingTemplate;

    // ─── REST Endpoints ──────────────────────────────────────────────────────

    @PostMapping("/api/live-sessions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Schedule a live session")
    public ResponseEntity<ApiResponse<LiveSession>> scheduleSession(
            @Valid @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID instructorId = UUID.fromString(jwt.getSubject());
        LiveSession session = liveSessionService.schedule(request, instructorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Session scheduled", session));
    }

    @PostMapping("/api/live-sessions/{id}/start")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Start a live session")
    public ResponseEntity<ApiResponse<LiveSession>> startSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID instructorId = UUID.fromString(jwt.getSubject());
        LiveSession session = liveSessionService.startSession(id, instructorId);
        return ResponseEntity.ok(ApiResponse.success("Session started", session));
    }

    @PostMapping("/api/live-sessions/{id}/end")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "End a live session")
    public ResponseEntity<ApiResponse<LiveSession>> endSession(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID instructorId = UUID.fromString(jwt.getSubject());
        LiveSession session = liveSessionService.endSession(id, instructorId);
        return ResponseEntity.ok(ApiResponse.success("Session ended", session));
    }

    @GetMapping("/api/live-sessions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all live sessions (upcoming + live)")
    public ResponseEntity<ApiResponse<List<LiveSession>>> getAllSessions() {
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.getAllSessions()));
    }

    @GetMapping("/api/courses/{courseId}/live-sessions")
    @Operation(summary = "List live sessions for a course")
    public ResponseEntity<ApiResponse<?>> getCourseSessions(@PathVariable UUID courseId) {
        return ResponseEntity.ok(ApiResponse.success(liveSessionService.getCourseSessions(courseId)));
    }

    // ─── WebSocket STOMP Handlers ────────────────────────────────────────────

    /**
     * Relay WebRTC signaling messages (offer, answer, ice-candidate).
     * Published to /topic/room/{roomId} for all participants.
     */
    @MessageMapping("/room/{roomId}/signal")
    public void relaySignal(@DestinationVariable UUID roomId, @Payload Map<String, Object> signal) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId, signal);
    }

    /**
     * Relay chat messages in live session.
     */
    @MessageMapping("/room/{roomId}/chat")
    public void relayChat(@DestinationVariable UUID roomId, @Payload Map<String, Object> message) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/chat", message);
    }

    /**
     * Raise hand and other participant events.
     */
    @MessageMapping("/room/{roomId}/events")
    public void relayEvent(@DestinationVariable UUID roomId, @Payload Map<String, Object> event) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/events", event);
    }
}
