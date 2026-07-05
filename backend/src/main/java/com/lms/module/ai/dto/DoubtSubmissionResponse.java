package com.lms.module.ai.dto;

import java.util.UUID;

/**
 * Response body returned immediately after a doubt is submitted (HTTP 202 Accepted).
 * <p>
 * The client uses {@code sessionId} to poll
 * {@code GET /api/v1/rag/doubts/{sessionId}} for the resolved answer,
 * or waits for a WebSocket push notification.
 */
public record DoubtSubmissionResponse(

        /** Reference ID for polling the doubt resolution status. */
        UUID sessionId,

        /** Human-readable confirmation message. */
        String message
) {}
