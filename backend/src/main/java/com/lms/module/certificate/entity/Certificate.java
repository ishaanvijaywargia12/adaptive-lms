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

    /**
     * Durable MinIO/R2 object key for the PDF (e.g. "certificates/tenant/courseId/studentId/certificate.pdf").
     * Use this to generate fresh presigned download URLs on demand.
     */
    @Column(name = "pdf_object_key")
    private String pdfObjectKey;

    /**
     * Durable MinIO/R2 object key for the QR code PNG.
     */
    @Column(name = "qr_object_key")
    private String qrObjectKey;

    /** @deprecated Use pdfObjectKey instead. Kept for backward compat. */
    @Column(name = "certificate_url")
    private String certificateUrl;

    /** @deprecated Use qrObjectKey instead. Kept for backward compat. */
    @Column(name = "qr_code_url")
    private String qrCodeUrl;

    @Column(name = "verification_code", nullable = false, unique = true)
    private UUID verificationCode;

    @Column(name = "is_valid", nullable = false)
    private boolean valid = true;
}
