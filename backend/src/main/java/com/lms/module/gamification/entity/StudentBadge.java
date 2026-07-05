package com.lms.module.gamification.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_badges")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StudentBadge extends BaseEntity {
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "badge_id", nullable = false)
    private UUID badgeId;

    @Column(name = "earned_at", nullable = false)
    private LocalDateTime earnedAt = LocalDateTime.now();
}
