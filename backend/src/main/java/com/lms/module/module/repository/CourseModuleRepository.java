package com.lms.module.module.repository;

import com.lms.module.module.entity.CourseModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseModuleRepository extends JpaRepository<CourseModule, UUID> {
    List<CourseModule> findByCourseIdOrderByOrderIndex(UUID courseId);

    @Query("SELECT m.courseId FROM CourseModule m WHERE m.id = :moduleId")
    Optional<UUID> findCourseIdByModuleId(UUID moduleId);
}
