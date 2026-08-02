package com.lms.module.lesson.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.lesson.entity.Lesson;
import com.lms.module.lesson.service.LessonService;
import com.lms.security.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lesson management endpoints.
 *
 * NOTE: The lesson-completion endpoint is intentionally NOT duplicated here.
 * Canonical endpoint: POST /api/lessons/{lessonId}/complete in EnrollmentController.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Lessons", description = "Lesson management & content endpoints")
public class LessonController {

    private final LessonService lessonService;
    private final CurrentUserService currentUserService;

    @PostMapping("/modules/{moduleId}/lessons")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Create a lesson in a module")
    public ResponseEntity<ApiResponse<Lesson>> createLesson(
            @PathVariable UUID moduleId,
            @RequestBody LessonService.CreateLessonRequest request) {
        Lesson lesson = lessonService.createLesson(moduleId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Lesson created", lesson));
    }

    @GetMapping("/modules/{moduleId}/lessons")
    @Operation(summary = "Get lessons for a module")
    public ResponseEntity<ApiResponse<List<Lesson>>> getLessons(@PathVariable UUID moduleId) {
        return ResponseEntity.ok(ApiResponse.success(lessonService.getLessonsForModule(moduleId)));
    }

    @PostMapping("/lessons/{id}/upload-url")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get presigned URL for content upload")
    public ResponseEntity<ApiResponse<Map<String, String>>> getUploadUrl(
            @PathVariable UUID id,
            @RequestParam String filename,
            @RequestParam String contentType) {
        return ResponseEntity.ok(ApiResponse.success(lessonService.getUploadUrl(id, filename, contentType)));
    }

    @PostMapping("/lessons/{id}/confirm-upload")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Confirm content uploaded and store durable object key")
    public ResponseEntity<ApiResponse<Lesson>> confirmUpload(
            @PathVariable UUID id,
            @RequestParam String objectKey) {
        return ResponseEntity.ok(ApiResponse.success("Upload confirmed", lessonService.confirmUpload(id, objectKey)));
    }

    @GetMapping("/lessons/{id}/content-url")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get a fresh presigned download URL for lesson content")
    public ResponseEntity<ApiResponse<Map<String, String>>> getContentUrl(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(lessonService.getContentUrl(id)));
    }

    @DeleteMapping("/lessons/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Delete a lesson")
    public ResponseEntity<ApiResponse<Void>> deleteLesson(@PathVariable UUID id) {
        lessonService.deleteLesson(id);
        return ResponseEntity.ok(ApiResponse.success("Lesson deleted", null));
    }
}
