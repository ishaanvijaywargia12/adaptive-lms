package com.lms.module.enrollment.repository;

import com.lms.module.enrollment.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    Optional<Enrollment> findByStudentIdAndCourseId(UUID studentId, UUID courseId);
    List<Enrollment> findByStudentId(UUID studentId);
    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);
    long countByCourseId(UUID courseId);
    long countByStudentId(UUID studentId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.courseId = :courseId AND e.completedAt IS NOT NULL")
    long countCompletedByCourseId(UUID courseId);

    @Query("SELECT COUNT(e) FROM Enrollment e WHERE e.studentId = :studentId AND e.completedAt IS NOT NULL")
    long countCompletedByStudentId(UUID studentId);

    @Query("SELECT COUNT(DISTINCT e.studentId) FROM Enrollment e " +
           "JOIN Course c ON e.courseId = c.id WHERE c.instructorId = :instructorId")
    long countByInstructorId(UUID instructorId);
}
