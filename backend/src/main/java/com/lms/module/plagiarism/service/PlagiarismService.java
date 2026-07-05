package com.lms.module.plagiarism.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.kafka.event.BaseEvent;
import com.lms.module.assignment.entity.Submission;
import com.lms.module.assignment.SubmissionRepository;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlagiarismService {

    private final SubmissionRepository submissionRepository;
    private final ObjectMapper objectMapper;
    private final Tika tika = new Tika();

    @KafkaListener(topics = "lms.assignment.submitted", groupId = "lms-plagiarism-group", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void handleSubmission(BaseEvent.AssignmentSubmittedEvent event) {
        TenantContext.setCurrentTenant(event.tenantId());
        try {
            Submission submission = submissionRepository.findById(event.submissionId()).orElse(null);
            if (submission == null || submission.isPlagiarismChecked()) return;

            String extractedText = extractText(submission);
            if (extractedText == null || extractedText.trim().isEmpty()) {
                submission.setPlagiarismChecked(true);
                submission.setPlagiarismScore(BigDecimal.ZERO);
                submissionRepository.save(submission);
                return;
            }

            // Fetch other submissions for the same assignment
            List<Submission> peerSubmissions = submissionRepository.findByAssignmentId(submission.getAssignmentId());
            
            double maxSimilarity = 0.0;
            Map<String, Object> report = new HashMap<>();
            report.put("matches", new ArrayList<Map<String, Object>>());

            for (Submission peer : peerSubmissions) {
                if (peer.getId().equals(submission.getId())) continue;

                String peerText = extractText(peer);
                if (peerText == null || peerText.trim().isEmpty()) continue;

                double similarity = calculateCosineSimilarity(extractedText, peerText);
                if (similarity > maxSimilarity) {
                    maxSimilarity = similarity;
                }

                if (similarity > 0.2) { // 20% match threshold
                    Map<String, Object> match = new HashMap<>();
                    match.put("peerSubmissionId", peer.getId());
                    match.put("peerStudentId", peer.getStudentId());
                    match.put("similarityScore", similarity * 100);
                    ((List<Map<String, Object>>) report.get("matches")).add(match);
                }
            }

            submission.setPlagiarismChecked(true);
            submission.setPlagiarismScore(BigDecimal.valueOf(maxSimilarity * 100));
            submission.setPlagiarismReport(objectMapper.writeValueAsString(report));
            submissionRepository.save(submission);
            
            log.info("Plagiarism check completed for submission {}: max similarity {}%", 
                    submission.getId(), maxSimilarity * 100);

        } catch (Exception e) {
            log.error("Failed to process plagiarism check for submission {}: {}", 
                    event.submissionId(), e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractText(Submission submission) {
        StringBuilder text = new StringBuilder();
        if (submission.getTextContent() != null) {
            text.append(submission.getTextContent()).append(" ");
        }
        
        if (submission.getFileUrl() != null && !submission.getFileUrl().isEmpty()) {
            try {
                if (submission.getFileUrl().startsWith("http")) {
                    try (InputStream is = new URL(submission.getFileUrl()).openStream()) {
                        String fileText = tika.parseToString(is);
                        text.append(fileText);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to extract text from file {}: {}", submission.getFileUrl(), e.getMessage());
            }
        }
        return text.toString();
    }

    private double calculateCosineSimilarity(String text1, String text2) {
        Map<String, Integer> wordCount1 = getWordCounts(text1);
        Map<String, Integer> wordCount2 = getWordCounts(text2);

        Set<String> uniqueWords = new HashSet<>(wordCount1.keySet());
        uniqueWords.addAll(wordCount2.keySet());

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (String word : uniqueWords) {
            int v1 = wordCount1.getOrDefault(word, 0);
            int v2 = wordCount2.getOrDefault(word, 0);
            
            dotProduct += v1 * v2;
            norm1 += Math.pow(v1, 2);
            norm2 += Math.pow(v2, 2);
        }

        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private Map<String, Integer> getWordCounts(String text) {
        Map<String, Integer> counts = new HashMap<>();
        String[] words = text.toLowerCase().replaceAll("[^a-z0-9 ]", "").split("\\s+");
        for (String word : words) {
            if (!word.isEmpty() && word.length() > 2) {
                counts.put(word, counts.getOrDefault(word, 0) + 1);
            }
        }
        return counts;
    }
}
