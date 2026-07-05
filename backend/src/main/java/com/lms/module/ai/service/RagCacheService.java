package com.lms.module.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.module.ai.dto.StructuredDoubtAnswer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed cache for RAG doubt answers.
 * <p>
 * <b>Cache key strategy:</b>
 * <pre>
 *   rag:answer:{SHA-256(tenantId + ":" + courseId + ":" + normalizedQuestion)}
 * </pre>
 * Normalization strips punctuation and lowercases the question so that
 * "What is polymorphism?" and "what is polymorphism" resolve to the same key,
 * eliminating redundant LLM calls for semantically identical questions.
 *
 * <p><b>TTL:</b> Configurable via {@code rag.cache-ttl-hours} (default 24 h).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RagCacheService {

    private static final String KEY_PREFIX = "rag:answer:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Retrieves a cached answer for the given tenant/course/question triple.
     *
     * @return {@code Optional.empty()} on cache miss or deserialization failure
     */
    public Optional<StructuredDoubtAnswer> get(String tenantId, UUID courseId, String question) {
        String key = buildCacheKey(tenantId, courseId, question);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            log.debug("[RAG-CACHE] MISS key={}", key);
            return Optional.empty();
        }
        try {
            StructuredDoubtAnswer answer = objectMapper.convertValue(cached, StructuredDoubtAnswer.class);
            log.debug("[RAG-CACHE] HIT key={}", key);
            return Optional.of(answer);
        } catch (Exception e) {
            log.warn("[RAG-CACHE] Deserialization failed for key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Stores an answer in Redis with a 24-hour TTL.
     */
    public void put(String tenantId, UUID courseId, String question, StructuredDoubtAnswer answer) {
        String key = buildCacheKey(tenantId, courseId, question);
        redisTemplate.opsForValue().set(key, answer, Duration.ofHours(24));
        log.debug("[RAG-CACHE] PUT key={}", key);
    }

    /**
     * Evicts all cached answers for a course (called when course material is re-indexed).
     * Note: Pattern-based deletion — use sparingly in production (SCAN-based).
     */
    public void evictByCourse(String tenantId, UUID courseId) {
        String pattern = KEY_PREFIX + tenantId + ":" + courseId + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("[RAG-CACHE] Evicted {} keys for tenantId={} courseId={}", keys.size(), tenantId, courseId);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Builds a stable, content-addressed cache key.
     * SHA-256 keeps the Redis key short regardless of question length.
     */
    private String buildCacheKey(String tenantId, UUID courseId, String question) {
        String normalized = normalizeQuestion(question);
        String raw = tenantId + ":" + courseId.toString() + ":" + normalized;
        return KEY_PREFIX + sha256Hex(raw);
    }

    /**
     * Normalizes a question for consistent cache key generation.
     * Lowercases, strips non-alphanumeric characters, and trims whitespace.
     */
    private String normalizeQuestion(String question) {
        return question.toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available on JVM — this should never happen
            log.error("[RAG-CACHE] SHA-256 unavailable, falling back to hashCode");
            return String.valueOf(input.hashCode());
        }
    }
}
