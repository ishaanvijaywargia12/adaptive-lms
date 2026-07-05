package com.lms.module.gamification.repository;

import com.lms.module.gamification.entity.Streak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StreakRepository extends JpaRepository<Streak, UUID> {
    Optional<Streak> findByStudentId(UUID studentId);
    List<Streak> findByLastActivityDateBefore(LocalDate date);
}
