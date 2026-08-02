package com.lms.module.quiz.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.quiz.dto.*;
import com.lms.module.quiz.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.lms.security.CurrentUserService;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
@Tag(name = "Quiz", description = "Quiz attempt and submission")
public class QuizController {

    private final CurrentUserService currentUserService;

    private final QuizService quizService;

    /**
     * Returns quiz metadata (no questions/answers). Used to show the quiz preview card.
     */
    @GetMapping("/by-lesson/{lessonId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Get quiz metadata by lesson ID")
    public ResponseEntity<ApiResponse<QuizDetailsDto>> getQuizByLesson(@PathVariable UUID lessonId) {
        return ResponseEntity.ok(ApiResponse.success("Quiz retrieved", quizService.getQuizByLessonId(lessonId)));
    }

    /**
     * Starts a quiz attempt and returns questions WITH options but WITHOUT correct answers.
     * isCorrect is intentionally excluded from OptionDto.
     */
    @PostMapping("/{quizId}/start")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Start a quiz attempt — returns questions (no correct answers)")
    public ResponseEntity<ApiResponse<QuizStartResponse>> startQuiz(
            @PathVariable UUID quizId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success("Quiz started", quizService.startAttempt(quizId, studentId)));
    }

    /**
     * Submits quiz answers and returns the detailed result including per-question review.
     * Correct answers and explanations are ONLY revealed in this response (post-submission).
     */
    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit quiz answers — reveals correct answers post-submission")
    public ResponseEntity<ApiResponse<QuizSubmitResponse>> submitQuiz(
            @PathVariable UUID attemptId,
            @Valid @RequestBody QuizSubmitRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success("Quiz submitted",
                quizService.submitAttempt(attemptId, studentId, request.answers())));
    }
}
