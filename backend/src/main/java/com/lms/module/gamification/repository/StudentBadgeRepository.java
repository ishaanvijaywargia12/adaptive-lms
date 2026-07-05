package com.lms.module.gamification.repository;

import com.lms.module.gamification.entity.StudentBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StudentBadgeRepository extends JpaRepository<StudentBadge, UUID> {
    @Query("SELECT sb.badgeId FROM StudentBadge sb WHERE sb.studentId = :studentId")
    List<UUID> findBadgeIdsByStudentId(UUID studentId);

    List<StudentBadge> findByStudentId(UUID studentId);
}
