package com.lms.module.quiz.repository;

import com.lms.module.quiz.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    long countByStudentIdAndQuizId(UUID studentId, UUID quizId);
    List<QuizAttempt> findByStudentIdAndQuizId(UUID studentId, UUID quizId);

    @Query("SELECT q.quizId, AVG(q.score) FROM QuizAttempt q WHERE q.studentId = :studentId GROUP BY q.quizId")
    List<Object[]> getAvgScoreByQuizForStudent(UUID studentId);

    @Query("SELECT c.categoryId, AVG(qa.score) FROM QuizAttempt qa " +
           "JOIN Quiz qz ON qa.quizId = qz.id " +
           "JOIN Lesson l ON qz.lessonId = l.id " +
           "JOIN CourseModule m ON l.moduleId = m.id " +
           "JOIN Course c ON m.courseId = c.id " +
           "WHERE qa.studentId = :studentId GROUP BY c.categoryId")
    Map<UUID, Double> getAvgScoreByCategoryForStudent(UUID studentId);
}
