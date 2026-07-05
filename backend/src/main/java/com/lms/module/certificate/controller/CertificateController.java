package com.lms.module.certificate.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.certificate.dto.CertificateDto;
import com.lms.module.certificate.entity.Certificate;
import com.lms.module.certificate.service.CertificateService;
import com.lms.module.course.entity.Course;
import com.lms.module.course.repository.CourseRepository;
import com.lms.module.user.entity.User;
import com.lms.module.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Tag(name = "Certificates", description = "Certificate generation and verification")
public class CertificateController {

    private final CertificateService certificateService;
    private final CourseRepository courseRepository;
    private final UserRepository userRepository;

    @GetMapping("/api/my/certificates")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my certificates with course info")
    public ResponseEntity<ApiResponse<List<CertificateDto>>> getMyCertificates(
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = UUID.fromString(jwt.getSubject());
        List<Certificate> certs = certificateService.getStudentCertificates(studentId);
        List<CertificateDto> dtos = certs.stream()
                .map(c -> toDto(c, studentId))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(dtos));
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

        return CertificateDto.builder()
                .id(cert.getId())
                .studentId(cert.getStudentId())
                .studentName(studentName)
                .courseId(cert.getCourseId())
                .courseTitle(courseTitle)
                .issuedAt(cert.getIssuedAt())
                .certificateUrl(cert.getCertificateUrl())
                .verificationCode(cert.getVerificationCode())
                .qrCodeUrl(cert.getQrCodeUrl())
                .valid(cert.isValid())
                .build();
    }
}
