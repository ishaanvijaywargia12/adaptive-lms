package com.lms.module.lesson.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks which lessons a student has completed within an enrollment.
 * One row per (enrollmentId, lessonId) pair.
 */
@Entity
@Table(
    name = "lesson_progress",
    uniqueConstraints = @UniqueConstraint(columnNames = {"enrollment_id", "lesson_id"})
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LessonProgress extends BaseEntity {

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "lesson_id", nullable = false)
    private UUID lessonId;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;
}
