package com.lms.module.assignment;

import com.lms.module.assignment.entity.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    Page<Submission> findByAssignmentId(UUID assignmentId, Pageable pageable);
    List<Submission> findByAssignmentId(UUID assignmentId);
    Page<Submission> findByStudentId(UUID studentId, Pageable pageable);
}
