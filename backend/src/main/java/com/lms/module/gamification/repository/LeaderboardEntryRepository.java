package com.lms.module.gamification.repository;

import com.lms.module.gamification.entity.LeaderboardEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LeaderboardEntryRepository extends JpaRepository<LeaderboardEntry, UUID> {
    List<LeaderboardEntry> findByPeriodAndCourseIdOrderByRankAsc(LeaderboardEntry.Period period, UUID courseId);
    Optional<LeaderboardEntry> findByStudentIdAndCourseIdAndPeriod(UUID studentId, UUID courseId, LeaderboardEntry.Period period);
}
