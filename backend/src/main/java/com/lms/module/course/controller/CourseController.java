package com.lms.module.course.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.course.dto.*;
import com.lms.module.course.entity.Course;
import com.lms.module.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import com.lms.security.CurrentUserService;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Tag(name = "Courses", description = "Course management endpoints")
public class CourseController {

    private final CurrentUserService currentUserService;

    private final CourseService courseService;

    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Create a new course")
    public ResponseEntity<ApiResponse<Course>> createCourse(
            @Valid @RequestBody CreateCourseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID instructorId = currentUserService.resolveOrProvision(jwt).getId();
        Course course = courseService.createCourse(request, instructorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Course created", course));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get course by ID")
    public ResponseEntity<ApiResponse<Course>> getCourse(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(courseService.getCourseById(id)));
    }

    @GetMapping
    @Operation(summary = "Browse courses with search and filters")
    public ResponseEntity<ApiResponse<Page<Course>>> searchCourses(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageRequest pageRequest = PageRequest.of(page, size);
        return ResponseEntity.ok(ApiResponse.success(courseService.searchCourses(search, category, difficulty, pageRequest)));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get all courses created by the authenticated instructor")
    public ResponseEntity<ApiResponse<java.util.List<Course>>> getMyCourses(
            @AuthenticationPrincipal Jwt jwt) {
        UUID instructorId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success(courseService.getCoursesByInstructor(instructorId)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update course")
    public ResponseEntity<ApiResponse<Course>> updateCourse(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success("Course updated", courseService.updateCourse(id, request, userId)));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @Operation(summary = "Submit course for review/publish")
    public ResponseEntity<ApiResponse<Course>> publishCourse(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        UUID instructorId = currentUserService.resolveOrProvision(jwt).getId();
        return ResponseEntity.ok(ApiResponse.success("Course submitted for review", courseService.publishCourse(id, instructorId)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve course (Admin only)")
    public ResponseEntity<ApiResponse<Course>> approveCourse(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Course approved", courseService.approveCourse(id)));
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all courses pending admin review")
    public ResponseEntity<ApiResponse<java.util.List<Course>>> getPendingCourses() {
        return ResponseEntity.ok(ApiResponse.success(courseService.getPendingCourses()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject course (Admin only)")
    public ResponseEntity<ApiResponse<Course>> rejectCourse(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Course rejected", courseService.rejectCourse(id)));
    }
}
