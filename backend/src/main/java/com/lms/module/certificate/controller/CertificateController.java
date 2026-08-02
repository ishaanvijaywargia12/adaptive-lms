package com.lms.module.certificate.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.certificate.dto.CertificateDto;
import com.lms.module.certificate.entity.Certificate;
import com.lms.module.certificate.service.CertificateService;
import com.lms.module.course.entity.Course;
import com.lms.module.course.repository.CourseRepository;
import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import com.lms.common.service.MinioStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import com.lms.security.CurrentUserService;

@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Certificates", description = "Certificate generation and verification")
public class CertificateController {

    private final CurrentUserService currentUserService;
    private final CertificateService certificateService;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final MinioStorageService minioStorageService;

    @GetMapping("/api/my/certificates")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my certificates with course info")
    public ResponseEntity<ApiResponse<List<CertificateDto>>> getMyCertificates(
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        List<Certificate> certs = certificateService.getStudentCertificates(studentId);
        List<CertificateDto> dtos = certs.stream()
                .map(c -> toDto(c, studentId))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    /**
     * Redirect to a fresh presigned URL for the certificate PDF.
     * The stored object key (from CertificateService) is resolved to a short-lived URL.
     */
    @GetMapping("/api/certificates/{id}/download")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Download certificate PDF (presigned redirect)")
    public ResponseEntity<Void> downloadCertificate(@PathVariable UUID id) {
        Certificate cert = certificateService.findById(id);
        try {
            String presignedUrl = minioStorageService.generatePresignedUrl(
                    "lms-certificates", cert.getPdfObjectKey(), 3600);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(presignedUrl)).build();
        } catch (Exception e) {
            // If MinIO is disabled, return the stored URL directly (base64 fallback)
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(cert.getCertificateUrl())).build();
        }
    }

    /**
     * Redirect to a fresh presigned URL for the certificate QR code PNG.
     */
    @GetMapping("/api/certificates/{id}/qr")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get certificate QR code (presigned redirect)")
    public ResponseEntity<Void> getCertificateQr(@PathVariable UUID id) {
        Certificate cert = certificateService.findById(id);
        try {
            String presignedUrl = minioStorageService.generatePresignedUrl(
                    "lms-certificates", cert.getQrObjectKey(), 3600);
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(presignedUrl)).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(cert.getQrCodeUrl())).build();
        }
    }

    /**
     * Public endpoint — no authentication required.
     * Used for QR code verification.
     */
    @GetMapping("/public/verify/{verificationCode}")
    @Operation(summary = "Verify a certificate (public, no auth)")
    public ResponseEntity<ApiResponse<CertificateDto>> verify(
            @PathVariable UUID verificationCode) {
        Certificate cert = certificateService.verifyCertificate(verificationCode);
        return ResponseEntity.ok(ApiResponse.success("Certificate is valid", toDto(cert, cert.getStudentId())));
    }

    // ─── Mapping Helpers ──────────────────────────────────────────────────────

    private CertificateDto toDto(Certificate cert, UUID studentId) {
        String courseTitle = courseRepository.findById(cert.getCourseId())
                .map(Course::getTitle)
                .orElse("Unknown Course");

        String studentName = userRepository.findById(studentId)
                .map(u -> (u.getFirstName() + " " + u.getLastName()).trim())
                .orElse("Learner");

        // Use API paths (presigned redirect) instead of raw object keys
        String downloadUrl = "/api/certificates/" + cert.getId() + "/download";
        String qrUrl       = "/api/certificates/" + cert.getId() + "/qr";

        return CertificateDto.builder()
                .id(cert.getId())
                .studentId(cert.getStudentId())
                .studentName(studentName)
                .courseId(cert.getCourseId())
                .courseTitle(courseTitle)
                .issuedAt(cert.getIssuedAt())
                .certificateUrl(downloadUrl)
                .verificationCode(cert.getVerificationCode())
                .qrCodeUrl(qrUrl)
                .valid(cert.isValid())
                .build();
    }
}
