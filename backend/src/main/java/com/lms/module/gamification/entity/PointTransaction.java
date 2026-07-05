package com.lms.module.gamification.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "point_transactions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointTransaction extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "reference_id")
    private UUID referenceId;
}
