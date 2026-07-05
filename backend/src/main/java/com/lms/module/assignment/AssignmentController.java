package com.lms.module.assignment;

import com.lms.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Assignments", description = "Assignment submission and grading")
public class AssignmentController {

    private final AssignmentService assignmentService;

    @PostMapping("/assignments/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Submit an assignment")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(
            @PathVariable UUID id,
            @RequestBody SubmitAssignmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID studentId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Submitted", assignmentService.submit(id, studentId, request)));
    }

    @GetMapping("/assignments/{id}/submissions")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "List all submissions for an assignment")
    public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> listSubmissions(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.listSubmissions(id, PageRequest.of(page, size))));
    }

    @PutMapping("/submissions/{id}/grade")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Grade a submission")
    public ResponseEntity<ApiResponse<Map<String, Object>>> grade(
            @PathVariable UUID id,
            @RequestBody GradeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID instructorId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success("Graded", assignmentService.grade(id, request, instructorId)));
    }

    @GetMapping("/submissions/{id}/plagiarism-report")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get plagiarism report for a submission")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlagiarismReport(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getPlagiarismReport(id)));
    }

    @GetMapping("/my/submissions")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get student's own submissions")
    public ResponseEntity<ApiResponse<Page<Map<String, Object>>>> mySubmissions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID studentId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getStudentSubmissions(studentId, PageRequest.of(page, size))));
    }

    public record SubmitAssignmentRequest(String textContent, String fileObjectKey) {}
    public record GradeRequest(Double score, String feedback) {}
}
