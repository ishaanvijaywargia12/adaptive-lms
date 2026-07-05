package com.lms.module.certificate.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Certificate DTO — includes course title and student name for frontend display.
 * Safer than returning the entity directly.
 */
@Data
@Builder
public class CertificateDto {
    private UUID id;
    private UUID studentId;
    private String studentName;
    private UUID courseId;
    private String courseTitle;
    private LocalDateTime issuedAt;
    private String certificateUrl;
    private UUID verificationCode;
    private String qrCodeUrl;
    private boolean valid;
}
