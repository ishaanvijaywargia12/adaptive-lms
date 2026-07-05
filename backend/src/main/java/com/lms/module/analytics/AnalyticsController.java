package com.lms.module.analytics;

import com.lms.common.response.ApiResponse;
import com.lms.module.enrollment.repository.EnrollmentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Instructor and student dashboard analytics")
public class AnalyticsController {

    private final EnrollmentRepository enrollmentRepository;

    @GetMapping("/instructor")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get instructor analytics dashboard summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> instructorDashboard(
            @AuthenticationPrincipal Jwt jwt) {
        UUID instructorId = UUID.fromString(jwt.getSubject());
        Map<String, Object> data = new HashMap<>();

        long totalEnrollments = enrollmentRepository.countByInstructorId(instructorId);
        data.put("totalEnrollments", totalEnrollments);
        data.put("instructorId", instructorId);
        // Additional metrics would be populated by a full analytics service in production
        data.put("avgCompletionRate", 0.0);
        data.put("activeStudentsThisWeek", 0);

        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @GetMapping("/student")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get student analytics dashboard summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> studentDashboard(
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = UUID.fromString(jwt.getSubject());
        Map<String, Object> data = new HashMap<>();

        long enrollments = enrollmentRepository.countByStudentId(studentId);
        long completed = enrollmentRepository.countCompletedByStudentId(studentId);
        data.put("totalEnrollments", enrollments);
        data.put("completedCourses", completed);
        data.put("inProgressCourses", enrollments - completed);
        data.put("studentId", studentId);

        return ResponseEntity.ok(ApiResponse.success(data));
    }
}
