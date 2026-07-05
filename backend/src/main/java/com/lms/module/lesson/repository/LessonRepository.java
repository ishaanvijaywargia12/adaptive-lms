package com.lms.module.lesson.repository;

import com.lms.module.lesson.entity.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    List<Lesson> findByModuleIdOrderByOrderIndex(UUID moduleId);

    @Query("SELECT COUNT(l) FROM Lesson l " +
           "JOIN CourseModule m ON l.moduleId = m.id " +
           "WHERE m.courseId = :courseId")
    long countByCourseId(UUID courseId);
}
