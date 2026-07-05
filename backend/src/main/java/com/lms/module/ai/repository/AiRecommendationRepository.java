package com.lms.module.ai.repository;

import com.lms.module.ai.entity.AiRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, UUID> {
    List<AiRecommendation> findByStudentIdAndDismissedFalseOrderByConfidenceScoreDesc(UUID studentId);
    void deleteByStudentId(UUID studentId);
}
