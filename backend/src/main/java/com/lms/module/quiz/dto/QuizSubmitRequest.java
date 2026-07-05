package com.lms.module.quiz.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Request body for POST /quizzes/attempts/{attemptId}/submit.
 * Maps questionId → selected option ID (as string UUID) or text answer.
 */
public record QuizSubmitRequest(
    @NotNull Map<UUID, String> answers
) {}
