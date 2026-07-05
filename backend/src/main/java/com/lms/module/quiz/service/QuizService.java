package com.lms.module.quiz.service;

import com.lms.common.exception.BusinessLogicException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.kafka.event.BaseEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.quiz.dto.*;
import com.lms.module.quiz.entity.*;
import com.lms.module.quiz.repository.*;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAnswerRepository quizAnswerRepository;
    private final OptionRepository optionRepository;
    private final KafkaProducerService kafkaProducer;
    private final RedisTemplate<String, Object> redisTemplate;

    // ─── Read ─────────────────────────────────────────────────────────────────

    public QuizDetailsDto getQuizByLessonId(UUID lessonId) {
        Quiz quiz = quizRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz for lesson", lessonId.toString()));
        int totalQuestions = questionRepository.countByQuizId(quiz.getId());
        return toDetailsDto(quiz, totalQuestions);
    }

    // ─── Start Attempt ────────────────────────────────────────────────────────

    @Transactional
    public QuizStartResponse startAttempt(UUID quizId, UUID studentId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId.toString()));

        // Enforce max attempts
        long attemptCount = quizAttemptRepository.countByStudentIdAndQuizId(studentId, quizId);
        if (quiz.getMaxAttempts() > 0 && attemptCount >= quiz.getMaxAttempts()) {
            throw new BusinessLogicException("Maximum attempts reached for this quiz");
        }

        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndex(quizId);
        if (quiz.isShuffleQuestions()) {
            Collections.shuffle(questions);
        }

        QuizAttempt attempt = QuizAttempt.builder()
                .studentId(studentId)
                .quizId(quizId)
                .startedAt(LocalDateTime.now())
                .attemptNumber((int) attemptCount + 1)
                .build();
        attempt = quizAttemptRepository.save(attempt);

        // Store timer in Redis if time-limited
        if (quiz.getTimeLimitSeconds() > 0) {
            String timerKey = "quiz:timer:" + attempt.getId();
            redisTemplate.opsForValue().set(timerKey, quiz.getTimeLimitSeconds(),
                    quiz.getTimeLimitSeconds(), TimeUnit.SECONDS);
        }

        // Build question DTOs with options (no correct answers!)
        List<QuestionDto> questionDtos = questions.stream()
                .map(q -> toQuestionDto(q, false)) // false = don't include correct answers
                .collect(Collectors.toList());

        log.info("Quiz attempt started: quizId={} studentId={} attemptNumber={}",
                quizId, studentId, attempt.getAttemptNumber());

        return QuizStartResponse.builder()
                .attemptId(attempt.getId())
                .quizId(quizId)
                .quizTitle(quiz.getTitle())
                .timeLimitSeconds(quiz.getTimeLimitSeconds())
                .passingScore(quiz.getPassingScore())
                .totalQuestions(questions.size())
                .questions(questionDtos)
                .build();
    }

    // ─── Submit Attempt ───────────────────────────────────────────────────────

    @Transactional
    public QuizSubmitResponse submitAttempt(UUID attemptId, UUID studentId, Map<UUID, String> answers) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz attempt", attemptId.toString()));

        if (!attempt.getStudentId().equals(studentId)) {
            throw new BusinessLogicException("Not your quiz attempt");
        }
        if (attempt.getCompletedAt() != null) {
            throw new BusinessLogicException("This attempt has already been submitted");
        }

        // Check timer
        String timerKey = "quiz:timer:" + attemptId;
        final UUID quizId = attempt.getQuizId();
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", quizId.toString()));

        boolean timerExpired = quiz.getTimeLimitSeconds() > 0
                && redisTemplate.opsForValue().get(timerKey) == null;
        if (timerExpired) {
            throw new BusinessLogicException("Quiz time limit exceeded");
        }

        List<Question> questions = questionRepository.findByQuizIdOrderByOrderIndex(quizId);
        int totalPoints = 0;
        int earnedPoints = 0;
        int correctCount = 0;
        List<QuizSubmitResponse.QuestionReview> reviews = new ArrayList<>();

        for (Question question : questions) {
            totalPoints += question.getPoints();
            String answer = answers.get(question.getId());

            boolean isCorrect = false;
            UUID selectedOptionId = null;
            int pointsEarned = 0;
            String selectedAnswer = null;
            String correctAnswer = null;

            if (question.getQuestionType() == Question.QuestionType.SHORT_ANSWER) {
                // Short answer: non-empty is accepted (instructor must review manually in full impl)
                isCorrect = answer != null && !answer.isBlank();
                selectedAnswer = answer;
                if (isCorrect) pointsEarned = question.getPoints();

            } else {
                // MCQ / TRUE_FALSE — look up selected option
                List<Option> options = optionRepository.findByQuestionId(question.getId());
                Option correctOption = options.stream().filter(Option::isCorrect).findFirst().orElse(null);
                correctAnswer = correctOption != null ? correctOption.getOptionText() : "—";

                if (answer != null) {
                    try {
                        UUID finalSelectedOptionId = UUID.fromString(answer);
                        selectedOptionId = finalSelectedOptionId;
                        Option selected = options.stream()
                                .filter(o -> o.getId().equals(finalSelectedOptionId))
                                .findFirst().orElse(null);
                        if (selected != null) {
                            selectedAnswer = selected.getOptionText();
                            if (selected.isCorrect()) {
                                isCorrect = true;
                                pointsEarned = question.getPoints();
                            }
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            if (isCorrect) correctCount++;
            earnedPoints += pointsEarned;

            QuizAnswer quizAnswer = QuizAnswer.builder()
                    .attemptId(attemptId)
                    .questionId(question.getId())
                    .selectedOptionId(selectedOptionId)
                    .textAnswer(question.getQuestionType() == Question.QuestionType.SHORT_ANSWER ? answer : null)
                    .correct(isCorrect)
                    .pointsEarned(BigDecimal.valueOf(pointsEarned))
                    .build();
            quizAnswerRepository.save(quizAnswer);

            reviews.add(QuizSubmitResponse.QuestionReview.builder()
                    .questionId(question.getId())
                    .questionText(question.getQuestionText())
                    .correct(isCorrect)
                    .selectedAnswer(selectedAnswer)
                    .correctAnswer(correctAnswer)
                    .explanation(question.getExplanation()) // Only revealed after submission
                    .pointsEarned(pointsEarned)
                    .totalPoints(question.getPoints())
                    .build());
        }

        double scorePercent = totalPoints > 0
                ? BigDecimal.valueOf(earnedPoints * 100.0 / totalPoints)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        boolean passed = scorePercent >= quiz.getPassingScore();

        attempt.setScore(BigDecimal.valueOf(scorePercent));
        attempt.setPassed(passed);
        attempt.setCompletedAt(LocalDateTime.now());
        quizAttemptRepository.save(attempt);

        // Clean up timer
        redisTemplate.delete(timerKey);

        // Publish Kafka events
        String tenantId = TenantContext.getCurrentTenant();
        if (passed) {
            kafkaProducer.publishQuizPassed(new BaseEvent.QuizPassedEvent(
                    UUID.randomUUID().toString(), tenantId, LocalDateTime.now(),
                    studentId, quiz.getId(), attemptId, scorePercent, null
            ));
        } else {
            kafkaProducer.publishQuizFailed(new BaseEvent.QuizFailedEvent(
                    UUID.randomUUID().toString(), tenantId, LocalDateTime.now(),
                    studentId, quiz.getId(), attemptId, scorePercent
            ));
        }

        log.info("Quiz attempt {} by student {} submitted: score={}%, passed={}", attemptId, studentId, scorePercent, passed);

        return QuizSubmitResponse.builder()
                .attemptId(attemptId)
                .scorePercent(scorePercent)
                .passed(passed)
                .totalQuestions(questions.size())
                .correctAnswers(correctCount)
                .pointsEarned(earnedPoints)
                .totalPoints(totalPoints)
                .questionReviews(reviews)
                .build();
    }

    // ─── Mapping Helpers ──────────────────────────────────────────────────────

    private QuizDetailsDto toDetailsDto(Quiz quiz, int totalQuestions) {
        return QuizDetailsDto.builder()
                .id(quiz.getId())
                .lessonId(quiz.getLessonId())
                .title(quiz.getTitle())
                .passingScore(quiz.getPassingScore())
                .timeLimitSeconds(quiz.getTimeLimitSeconds())
                .maxAttempts(quiz.getMaxAttempts())
                .shuffleQuestions(quiz.isShuffleQuestions())
                .totalQuestions(totalQuestions)
                .build();
    }

    private QuestionDto toQuestionDto(Question question, boolean includeCorrectAnswers) {
        List<Option> options = optionRepository.findByQuestionId(question.getId());
        List<OptionDto> optionDtos = options.stream()
                .map(o -> OptionDto.builder()
                        .id(o.getId())
                        .optionText(o.getOptionText())
                        // isCorrect is NEVER included in OptionDto — it's safely excluded by design
                        .build())
                .collect(Collectors.toList());

        return QuestionDto.builder()
                .id(question.getId())
                .questionText(question.getQuestionText())
                .questionType(question.getQuestionType())
                .difficulty(question.getDifficulty())
                .points(question.getPoints())
                .orderIndex(question.getOrderIndex())
                .options(optionDtos)
                .build();
    }
}
