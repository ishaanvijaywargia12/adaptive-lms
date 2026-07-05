package com.lms.module.certificate.entity;

import com.lms.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "certificates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Certificate extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "certificate_url")
    private String certificateUrl;

    @Column(name = "verification_code", nullable = false, unique = true)
    private UUID verificationCode;

    @Column(name = "qr_code_url")
    private String qrCodeUrl;

    @Column(name = "is_valid", nullable = false)
    private boolean valid = true;
}
