package com.lms.module.ai.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "ai_recommendations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AiRecommendation extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "recommended_course_id", nullable = false)
    private UUID recommendedCourseId;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "is_dismissed", nullable = false)
    private boolean dismissed = false;
}
