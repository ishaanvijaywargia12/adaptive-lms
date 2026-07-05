package com.lms.module.live.repository;

import com.lms.module.live.entity.LiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LiveSessionRepository extends JpaRepository<LiveSession, UUID> {
    List<LiveSession> findByCourseIdOrderByScheduledAtDesc(UUID courseId);
    List<LiveSession> findByInstructorId(UUID instructorId);
    List<LiveSession> findByStatusInOrderByScheduledAtDesc(List<LiveSession.SessionStatus> statuses);
}
