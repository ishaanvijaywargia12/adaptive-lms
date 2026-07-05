package com.lms.module.ai.service;

import com.lms.module.ai.entity.AiRecommendation;
import com.lms.module.ai.repository.AiRecommendationRepository;
import com.lms.module.course.entity.Course;
import com.lms.module.course.repository.CourseRepository;
import com.lms.module.enrollment.repository.EnrollmentRepository;
import com.lms.module.quiz.repository.QuizAttemptRepository;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final AiRecommendationRepository recommendationRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Rule-based weighted recommendation algorithm.
     * Inputs: quiz performance per category, completed lessons, difficulty history, current enrollments.
     * Scores each non-enrolled published course, returns top 5.
     */
    @Transactional
    public List<AiRecommendation> generateRecommendations(UUID studentId) {
        String tenant = TenantContext.getCurrentTenant();
        String cacheKey = "recommendations:" + tenant + ":" + studentId;

        // Clear existing recommendations
        recommendationRepository.deleteByStudentId(studentId);

        // Fetch all published courses
        List<Course> publishedCourses = courseRepository.findPublishedCourses();

        // Fetch enrolled course IDs
        Set<UUID> enrolledCourseIds = enrollmentRepository.findByStudentId(studentId)
                .stream().map(e -> e.getCourseId()).collect(Collectors.toSet());

        // Calculate weak areas: categories where avg quiz score < 70%
        Map<UUID, Double> categoryAvgScores = quizAttemptRepository
                .getAvgScoreByCategoryForStudent(studentId);

        Set<UUID> weakCategoryIds = categoryAvgScores.entrySet().stream()
                .filter(e -> e.getValue() < 70.0).map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        Set<UUID> strongCategoryIds = categoryAvgScores.entrySet().stream()
                .filter(e -> e.getValue() >= 85.0).map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        // Highest difficulty completed by student
        Course.Difficulty maxDifficultyCompleted = Course.Difficulty.BEGINNER;
        if (!enrolledCourseIds.isEmpty()) {
            List<com.lms.module.enrollment.entity.Enrollment> enrollments = enrollmentRepository.findByStudentId(studentId);
            maxDifficultyCompleted = enrollments.stream()
                .filter(e -> e.getCompletedAt() != null)
                .map(e -> courseRepository.findById(e.getCourseId()).map(Course::getDifficulty).orElse(Course.Difficulty.BEGINNER))
                .max(Comparator.naturalOrder())
                .orElse(Course.Difficulty.BEGINNER);
        }

        Course.Difficulty nextDifficulty = nextLevel(maxDifficultyCompleted);

        // Score all courses
        List<ScoredCourse> scored = new ArrayList<>();
        for (Course course : publishedCourses) {
            if (enrolledCourseIds.contains(course.getId())) continue;

            int score = 0;
            StringBuilder reason = new StringBuilder();

            // +30: category matches weak area (reinforcement)
            if (weakCategoryIds.contains(course.getCategoryId())) {
                score += 30;
                reason.append("Reinforces your weak area. ");
            }

            // +20: next difficulty level
            if (course.getDifficulty() == nextDifficulty) {
                score += 20;
                reason.append("Next step in your learning path. ");
            }

            // +10: trending (check Redis sorted set for enrollment count this week)
            String trendingKey = "trending:" + tenant + ":courses:" + weekStart();
            Double trendScore = redisTemplate.opsForZSet().score(trendingKey, course.getId().toString());
            if (trendScore != null && trendScore >= 10) {
                score += 10;
                reason.append("Trending this week. ");
            }

            // -20: already enrolled
            if (enrolledCourseIds.contains(course.getId())) {
                score -= 20;
            }

            if (score > 0) {
                scored.add(new ScoredCourse(course, score, reason.toString().trim()));
            }
        }

        // Sort by score desc, take top 5
        scored.sort((a, b) -> b.score - a.score);
        List<ScoredCourse> top5 = scored.stream().limit(5).collect(Collectors.toList());

        List<AiRecommendation> recommendations = top5.stream().map(sc -> {
            double confidence = Math.min(100.0, sc.score() * 2.0);
            return AiRecommendation.builder()
                    .studentId(studentId)
                    .recommendedCourseId(sc.course().getId())
                    .reason(sc.reason())
                    .confidenceScore(confidence)
                    .dismissed(false)
                    .build();
        }).collect(Collectors.toList());

        List<AiRecommendation> saved = recommendationRepository.saveAll(recommendations);

        // Cache in Redis for 1 hour
        redisTemplate.opsForValue().set(cacheKey, saved, 1, TimeUnit.HOURS);

        log.info("Generated {} recommendations for student {}", saved.size(), studentId);
        return saved;
    }

    @SuppressWarnings("unchecked")
    public List<AiRecommendation> getRecommendations(UUID studentId) {
        String tenant = TenantContext.getCurrentTenant();
        String cacheKey = "recommendations:" + tenant + ":" + studentId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return (List<AiRecommendation>) cached;
        return generateRecommendations(studentId);
    }

    @Transactional
    public void dismiss(UUID recommendationId, UUID studentId) {
        recommendationRepository.findById(recommendationId).ifPresent(r -> {
            if (r.getStudentId().equals(studentId)) {
                r.setDismissed(true);
                recommendationRepository.save(r);
            }
        });
    }

    private Course.Difficulty nextLevel(Course.Difficulty current) {
        return switch (current) {
            case BEGINNER -> Course.Difficulty.INTERMEDIATE;
            case INTERMEDIATE -> Course.Difficulty.ADVANCED;
            case ADVANCED -> Course.Difficulty.ADVANCED;
        };
    }

    private String weekStart() {
        return java.time.LocalDate.now().with(java.time.DayOfWeek.MONDAY).toString();
    }

    private record ScoredCourse(Course course, int score, String reason) {}
}
