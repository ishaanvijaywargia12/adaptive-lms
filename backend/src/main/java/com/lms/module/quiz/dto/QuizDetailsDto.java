package com.lms.module.quiz.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for GET /quizzes/by-lesson/{lessonId} and GET /quizzes/{quizId}.
 * Never includes questions with correct answers.
 */
@Data
@Builder
public class QuizDetailsDto {
    private UUID id;
    private UUID lessonId;
    private String title;
    private int passingScore;
    private int timeLimitSeconds;
    private int maxAttempts;
    private boolean shuffleQuestions;
    private int totalQuestions;
}
