package com.lms.module.ai.repository;

import com.lms.module.ai.entity.DoubtSession;
import com.lms.module.ai.entity.DoubtStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link DoubtSession} entities.
 * <p>
 * All queries are scoped to the current tenant schema via Hibernate's
 * schema-based multi-tenancy resolver ({@code TenantIdentifierResolver}).
 */
@Repository
public interface DoubtSessionRepository extends JpaRepository<DoubtSession, UUID> {

    /**
     * Returns all doubt sessions for a student, ordered newest-first.
     * Used by the student's doubt history page.
     */
    List<DoubtSession> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    /**
     * Returns all doubt sessions for a course filtered by status.
     * Used by instructors to review pending/resolved doubts.
     */
    List<DoubtSession> findByCourseIdAndStatusOrderByCreatedAtDesc(UUID courseId, DoubtStatus status);

    /**
     * Counts pending doubts per course — useful for instructor dashboard.
     */
    long countByCourseIdAndStatus(UUID courseId, DoubtStatus status);
}
