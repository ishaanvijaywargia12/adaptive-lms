package com.lms.module.quiz.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Safe option DTO — never exposes isCorrect before submission.
 */
@Data
@Builder
public class OptionDto {
    private UUID id;
    private String optionText;
}
