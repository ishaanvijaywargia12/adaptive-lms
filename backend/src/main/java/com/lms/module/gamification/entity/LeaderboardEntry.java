package com.lms.module.gamification.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "leaderboard_entries")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LeaderboardEntry extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id")
    private UUID courseId; // null = global leaderboard

    @Column(name = "total_points", nullable = false)
    private long totalPoints;

    @Column(name = "rank")
    private int rank;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false)
    private Period period = Period.ALL_TIME;

    public enum Period { WEEKLY, MONTHLY, ALL_TIME }
}
