package com.lms.module.quiz.dto;

import com.lms.module.quiz.entity.Question;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Safe question DTO — never exposes explanation or answer before submission.
 */
@Data
@Builder
public class QuestionDto {
    private UUID id;
    private String questionText;
    private Question.QuestionType questionType;
    private Question.Difficulty difficulty;
    private int points;
    private int orderIndex;
    private List<OptionDto> options;
}
