package com.lms.module.quiz.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for POST /quizzes/attempts/{attemptId}/submit.
 * Includes per-question review (correct/incorrect + explanation) AFTER submission.
 */
@Data
@Builder
public class QuizSubmitResponse {
    private UUID attemptId;
    private double scorePercent;
    private boolean passed;
    private int totalQuestions;
    private int correctAnswers;
    private int pointsEarned;
    private int totalPoints;
    private List<QuestionReview> questionReviews;

    @Data
    @Builder
    public static class QuestionReview {
        private UUID questionId;
        private String questionText;
        private boolean correct;
        private String selectedAnswer;       // Text of selected option
        private String correctAnswer;        // Text of correct option (revealed post-submit)
        private String explanation;          // Only revealed after submission
        private int pointsEarned;
        private int totalPoints;
    }
}
