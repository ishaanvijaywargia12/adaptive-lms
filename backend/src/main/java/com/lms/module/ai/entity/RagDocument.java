package com.lms.module.ai.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rag_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagDocument extends BaseEntity {

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "object_key", nullable = false)
    private String objectKey;

    @Column(name = "filename", nullable = false)
    private String filename;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.UPLOADED;

    @Column(name = "chunk_count")
    private Integer chunkCount = 0;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "indexed_at")
    private LocalDateTime indexedAt;

    public enum Status {
        UPLOADED, INDEXING, INDEXED, FAILED
    }
}
