package com.lms.module.course.repository;

import com.lms.module.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID>, JpaSpecificationExecutor<Course> {
    List<Course> findByInstructorId(UUID instructorId);
    List<Course> findByInstructorIdOrderByCreatedAtDesc(UUID instructorId);
    List<Course> findByStatus(Course.CourseStatus status);
    long countByStatus(Course.CourseStatus status);

    @Query("SELECT c FROM Course c WHERE c.status = 'PUBLISHED' AND c.archived = false ORDER BY c.createdAt DESC")
    List<Course> findPublishedCourses();
}
