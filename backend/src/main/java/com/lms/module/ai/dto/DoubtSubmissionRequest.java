package com.lms.module.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for submitting a student doubt.
 *
 * <p>Example JSON:
 * <pre>
 * {
 *   "courseId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
 *   "question": "What is the difference between abstract classes and interfaces in Java?"
 * }
 * </pre>
 */
public record DoubtSubmissionRequest(

        @NotNull(message = "courseId is required")
        UUID courseId,

        @NotBlank(message = "question must not be blank")
        @Size(min = 10, max = 2000,
              message = "question must be between 10 and 2000 characters")
        String question
) {}
