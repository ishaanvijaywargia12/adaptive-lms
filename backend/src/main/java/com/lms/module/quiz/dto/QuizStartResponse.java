package com.lms.module.quiz.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for POST /quizzes/{quizId}/start.
 * Questions include options but never expose correct answers.
 */
@Data
@Builder
public class QuizStartResponse {
    private UUID attemptId;
    private UUID quizId;
    private String quizTitle;
    private int timeLimitSeconds;
    private int passingScore;
    private int totalQuestions;
    private List<QuestionDto> questions;
}
