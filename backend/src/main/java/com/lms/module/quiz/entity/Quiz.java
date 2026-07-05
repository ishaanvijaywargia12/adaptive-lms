package com.lms.module.quiz.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "quizzes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Quiz extends BaseEntity {

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "passing_score", nullable = false)
    private int passingScore = 70;

    @Column(name = "time_limit_seconds")
    private int timeLimitSeconds;

    @Column(name = "max_attempts")
    private int maxAttempts = 3;

    @Column(name = "shuffle_questions", nullable = false)
    private boolean shuffleQuestions = false;
}
