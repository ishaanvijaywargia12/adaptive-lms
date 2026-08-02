package com.lms.module.ai.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persists every student doubt submission and its RAG-generated answer.
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
     * JSON array of sources used for generating the answer.
     */
    @Column(name = "sources_json", columnDefinition = "JSONB")
    private String sourcesJson;

    /**
     * Error message if status is FAILED.
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Current lifecycle state of this doubt session.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DoubtStatus status = DoubtStatus.PENDING;

    /** Timestamp when the LLM answer was stored (null until RESOLVED). */
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
