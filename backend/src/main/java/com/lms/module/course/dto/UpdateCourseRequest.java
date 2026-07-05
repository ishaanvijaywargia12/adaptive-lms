package com.lms.module.course.dto;

import com.lms.module.course.entity.Course;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateCourseRequest(
        String title,
        String description,
        UUID categoryId,
        Course.Difficulty difficulty,
        String[] tags,
        BigDecimal price,
        String thumbnailUrl
) {}
