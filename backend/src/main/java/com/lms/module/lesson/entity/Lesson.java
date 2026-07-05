package com.lms.module.lesson.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "lessons")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Lesson extends BaseEntity {

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private ContentType contentType;

    @Column(name = "content_url")
    private String contentUrl;

    @Column(name = "content_text", columnDefinition = "TEXT")
    private String contentText;

    @Column(name = "duration_seconds")
    private int durationSeconds;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "is_preview", nullable = false)
    private boolean preview = false;

    public enum ContentType { VIDEO, TEXT, PDF, QUIZ }
}
