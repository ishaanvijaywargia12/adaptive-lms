package com.lms.module.course.dto;

import com.lms.module.course.entity.Course;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCourseRequest(
        @NotBlank @Size(max = 500) String title,
        String description,
        UUID categoryId,
        Course.Difficulty difficulty,
        String[] tags,
        BigDecimal price,
        String thumbnailUrl
) {}
