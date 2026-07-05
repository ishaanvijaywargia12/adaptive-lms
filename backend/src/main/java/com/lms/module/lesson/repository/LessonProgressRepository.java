package com.lms.module.lesson.repository;

import com.lms.module.lesson.entity.LessonProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LessonProgressRepository extends JpaRepository<LessonProgress, UUID> {

    /**
     * Count completed lessons for a specific enrollment.
     */
    long countByEnrollmentId(UUID enrollmentId);

    /**
     * Check if a specific lesson has already been completed within an enrollment (dedup guard).
     */
    boolean existsByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId);

    Optional<LessonProgress> findByEnrollmentIdAndLessonId(UUID enrollmentId, UUID lessonId);
}
