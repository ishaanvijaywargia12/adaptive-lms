package com.lms.module.gamification.repository;

import com.lms.module.gamification.entity.PointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, UUID> {
    @Query("SELECT COALESCE(SUM(p.points), 0) FROM PointTransaction p WHERE p.studentId = :studentId")
    long sumPointsByStudentId(UUID studentId);

    boolean existsByStudentIdAndType(UUID studentId, String type);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Query("SELECT p.studentId, SUM(p.points) as total FROM PointTransaction p GROUP BY p.studentId ORDER BY total DESC LIMIT :topN")
    List<Object[]> findTopStudentsByPoints(int topN);
}
