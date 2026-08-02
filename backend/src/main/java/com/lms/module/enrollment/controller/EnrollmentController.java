package com.lms.module.enrollment.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.enrollment.entity.Enrollment;
import com.lms.module.enrollment.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.lms.security.CurrentUserService;

@RestController
@RequiredArgsConstructor
@Tag(name = "Enrollment", description = "Course enrollment and progress tracking")
public class EnrollmentController {

    private final CurrentUserService currentUserService;

    private final EnrollmentService enrollmentService;

    @PostMapping("/api/courses/{courseId}/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Enroll in a course")
    public ResponseEntity<ApiResponse<Enrollment>> enroll(
            @PathVariable UUID courseId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        Enrollment enrollment = enrollmentService.enroll(studentId, courseId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Enrolled successfully", enrollment));
    }

    @GetMapping("/api/my/enrollments")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my enrollments")
    public ResponseEntity<ApiResponse<List<Enrollment>>> getMyEnrollments(@AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success(enrollmentService.getMyEnrollments(studentId)));
    }

    @PostMapping("/api/lessons/{lessonId}/complete")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Mark lesson as completed")
    public ResponseEntity<ApiResponse<Void>> completeLesson(
            @PathVariable UUID lessonId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = currentUserService.resolveOrProvision(jwt).getId();
        enrollmentService.completeLesson(studentId, lessonId);
        return ResponseEntity.ok(ApiResponse.success("Lesson marked as complete", null));
    }
}
