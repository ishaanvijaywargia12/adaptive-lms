package com.lms.module.module.controller;

import com.lms.common.response.ApiResponse;
import com.lms.module.module.entity.CourseModule;
import com.lms.module.module.service.CourseModuleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Modules", description = "Course module management")
public class CourseModuleController {

    private final CourseModuleService moduleService;

    @PostMapping("/courses/{courseId}/modules")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Add a module to a course")
    public ResponseEntity<ApiResponse<CourseModule>> createModule(
            @PathVariable UUID courseId,
            @RequestBody CourseModuleService.CreateModuleRequest request) {
        CourseModule module = moduleService.createModule(courseId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Module created", module));
    }

    @GetMapping("/courses/{courseId}/modules")
    @Operation(summary = "List all modules of a course")
    public ResponseEntity<ApiResponse<List<CourseModule>>> getModules(@PathVariable UUID courseId) {
        return ResponseEntity.ok(ApiResponse.success(moduleService.getModulesForCourse(courseId)));
    }

    @PutMapping("/modules/{moduleId}")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update a module")
    public ResponseEntity<ApiResponse<CourseModule>> updateModule(
            @PathVariable UUID moduleId,
            @RequestBody CourseModuleService.UpdateModuleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Module updated", moduleService.updateModule(moduleId, request)));
    }

    @DeleteMapping("/modules/{moduleId}")
    @PreAuthorize("hasRole('INSTRUCTOR') or hasRole('ADMIN')")
    @Operation(summary = "Delete a module")
    public ResponseEntity<ApiResponse<Void>> deleteModule(@PathVariable UUID moduleId) {
        moduleService.deleteModule(moduleId);
        return ResponseEntity.ok(ApiResponse.success("Module deleted", null));
    }
}
