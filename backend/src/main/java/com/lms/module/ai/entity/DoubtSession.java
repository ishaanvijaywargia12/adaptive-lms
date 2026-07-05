package com.lms.module.ai.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persists every student doubt submission and its RAG-generated answer.
 * <p>
 * This table serves three purposes:
 * <ol>
 *   <li>Polling endpoint — frontend polls {@code GET /api/v1/rag/doubts/{sessionId}}
 *       to check status before the WebSocket notification arrives.</li>
 *   <li>Audit trail — instructors can review all doubts and answers per course.</li>
 *   <li>Analytics — answer latency, PENDING ratio, and DLQ volume can be derived
 *       from this table.</li>
 * </ol>
 *
 * <p>Maps to the {@code doubt_sessions} table defined in
 * {@code V2__create_tenant_schema.sql} (per-tenant schema).
 */
@Entity
@Table(name = "doubt_sessions",
        indexes = {
                @Index(name = "idx_doubt_sessions_student", columnList = "student_id"),
                @Index(name = "idx_doubt_sessions_course",  columnList = "course_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DoubtSession extends BaseEntity {

    /** The student who submitted the doubt. */
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    /** Course whose material was searched in Qdrant. */
    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    /** Raw question text as submitted by the student. */
    @Column(name = "question_text", columnDefinition = "TEXT", nullable = false)
    private String questionText;

    /**
     * LLM-generated answer injected after the RAG pipeline completes.
     * Null while {@link #status} is {@link DoubtStatus#PENDING}.
     */
    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    /**
     * Current lifecycle state of this doubt session.
     * Starts as {@link DoubtStatus#PENDING}, transitions to
     * {@link DoubtStatus#RESOLVED} or {@link DoubtStatus#FAILED}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DoubtStatus status = DoubtStatus.PENDING;

    /** Timestamp when the LLM answer was stored (null until RESOLVED). */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
