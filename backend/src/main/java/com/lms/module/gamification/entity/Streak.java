package com.lms.module.gamification.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "streaks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Streak extends BaseEntity {

    @Column(name = "student_id", nullable = false, unique = true)
    private UUID studentId;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak = 0;

    @Column(name = "last_activity_date")
    private LocalDate lastActivityDate;
}
