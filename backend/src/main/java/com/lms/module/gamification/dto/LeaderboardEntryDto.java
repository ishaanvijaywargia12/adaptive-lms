package com.lms.module.gamification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardEntryDto {
    private UUID studentId;
    private String studentName;
    private String avatarUrl;
    private long totalPoints;
    private int rank;
    private String period;   // "ALL_TIME" | "WEEKLY"
    private String scope;    // "GLOBAL" | course UUID string
}
