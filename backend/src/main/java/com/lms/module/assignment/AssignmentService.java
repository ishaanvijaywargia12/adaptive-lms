package com.lms.module.assignment;

import com.lms.common.exception.ResourceNotFoundException;
import com.lms.kafka.event.BaseEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.assignment.entity.Assignment;
import com.lms.module.assignment.entity.Submission;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final KafkaProducerService kafkaProducer;

    @Transactional
    public Map<String, Object> submit(UUID assignmentId, UUID studentId, AssignmentController.SubmitAssignmentRequest req) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

        Submission submission = Submission.builder()
                .assignmentId(assignmentId)
                .studentId(studentId)
                .textContent(req.textContent())
                .fileUrl(req.fileObjectKey())
                .submittedAt(LocalDateTime.now())
                .plagiarismChecked(false)
                .build();
        submission = submissionRepository.save(submission);

        // Publish to Kafka for async plagiarism check
        kafkaProducer.publishAssignmentSubmitted(new BaseEvent.AssignmentSubmittedEvent(
                UUID.randomUUID().toString(), TenantContext.getCurrentTenant(), LocalDateTime.now(),
                submission.getId(), assignmentId, studentId, null  // courseId null - resolved by consumer
        ));

        return toMap(submission);
    }

    public Page<Map<String, Object>> listSubmissions(UUID assignmentId, Pageable pageable) {
        Page<Submission> page = submissionRepository.findByAssignmentId(assignmentId, pageable);
        List<Map<String, Object>> mapped = page.getContent().stream().map(this::toMap).collect(Collectors.toList());
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    @Transactional
    public Map<String, Object> grade(UUID submissionId, AssignmentController.GradeRequest req, UUID instructorId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));
        submission.setScore(BigDecimal.valueOf(req.score()));
        submission.setFeedback(req.feedback());
        submission.setGradedAt(LocalDateTime.now());
        submission = submissionRepository.save(submission);

        kafkaProducer.publishAssignmentGraded(new BaseEvent.AssignmentGradedEvent(
                UUID.randomUUID().toString(), TenantContext.getCurrentTenant(), LocalDateTime.now(),
                submissionId, submission.getStudentId(), instructorId,
                req.score(), submission.getAssignmentId()
        ));

        return toMap(submission);
    }

    public Map<String, Object> getPlagiarismReport(UUID submissionId) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found: " + submissionId));
        Map<String, Object> report = new HashMap<>();
        report.put("submissionId", submission.getId());
        report.put("plagiarismScore", submission.getPlagiarismScore());
        report.put("plagiarismChecked", submission.isPlagiarismChecked());
        report.put("report", submission.getPlagiarismReport() != null ? submission.getPlagiarismReport() : "{}");
        return report;
    }

    public Page<Map<String, Object>> getStudentSubmissions(UUID studentId, Pageable pageable) {
        Page<Submission> page = submissionRepository.findByStudentId(studentId, pageable);
        List<Map<String, Object>> mapped = page.getContent().stream().map(this::toMap).collect(Collectors.toList());
        return new PageImpl<>(mapped, pageable, page.getTotalElements());
    }

    private Map<String, Object> toMap(Submission s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("assignmentId", s.getAssignmentId());
        m.put("studentId", s.getStudentId());
        m.put("textContent", s.getTextContent());
        m.put("fileUrl", s.getFileUrl());
        m.put("score", s.getScore());
        m.put("feedback", s.getFeedback());
        m.put("submittedAt", s.getSubmittedAt());
        m.put("gradedAt", s.getGradedAt());
        m.put("plagiarismScore", s.getPlagiarismScore());
        m.put("plagiarismChecked", s.isPlagiarismChecked());
        return m;
    }
}
