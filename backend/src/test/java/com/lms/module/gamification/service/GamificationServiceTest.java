package com.lms.module.gamification.service;

import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.gamification.entity.PointTransaction;
import com.lms.module.gamification.entity.Streak;
import com.lms.module.gamification.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    @Mock
    private PointTransactionRepository pointTransactionRepository;

    @Mock
    private StreakRepository streakRepository;

    @Mock
    private BadgeRepository badgeRepository;

    @Mock
    private StudentBadgeRepository studentBadgeRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ZSetOperations<String, Object> zSetOperations;

    @Mock
    private KafkaProducerService kafkaProducer;

    @InjectMocks
    private GamificationService gamificationService;

    @Test
    @DisplayName("award: skips duplicate awards when idempotencyKey exists")
    void awardSkipsDuplicates() {
        UUID studentId = UUID.randomUUID();
        String idemKey = "LESSON_COMPLETE:event-123";

        when(pointTransactionRepository.existsByIdempotencyKey(idemKey)).thenReturn(true);

        gamificationService.award(studentId, "LESSON_COMPLETE", UUID.randomUUID(), 10, idemKey);

        verify(pointTransactionRepository, never()).save(any());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("award: awards points and updates Redis leaderboard when new")
    void awardSavesAndUpdatesRedis() {
        UUID studentId = UUID.randomUUID();
        String idemKey = "LESSON_COMPLETE:event-456";

        when(pointTransactionRepository.existsByIdempotencyKey(idemKey)).thenReturn(false);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        gamificationService.award(studentId, "LESSON_COMPLETE", UUID.randomUUID(), 10, idemKey);

        verify(pointTransactionRepository, times(1)).save(any(PointTransaction.class));
        verify(zSetOperations, atLeastOnce()).incrementScore(anyString(), eq(studentId.toString()), eq(10.0));
    }

    @Test
    @DisplayName("recordStreakActivity: extends streak and awards bonus on consecutive days")
    void recordStreakActivityExtendsStreak() {
        UUID studentId = UUID.randomUUID();
        LocalDate yesterday = LocalDate.now().minusDays(1);

        Streak streak = Streak.builder()
                .studentId(studentId)
                .currentStreak(3)
                .longestStreak(5)
                .lastActivityDate(yesterday)
                .build();

        when(streakRepository.findByStudentId(studentId)).thenReturn(Optional.of(streak));
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        gamificationService.recordStreakActivity(studentId);

        assertThat(streak.getCurrentStreak()).isEqualTo(4);
        assertThat(streak.getLastActivityDate()).isEqualTo(LocalDate.now());
        verify(streakRepository, times(1)).save(streak);
    }
}
