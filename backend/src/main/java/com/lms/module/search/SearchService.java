package com.lms.module.search;

import com.lms.module.course.entity.Course;
import com.lms.module.course.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Search service that uses JPA database search as primary implementation.
 * In production, Elasticsearch would be the primary backend; JPA is the fallback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final CourseRepository courseRepository;

    public Map<String, Object> searchCourses(
            String query,
            String category,
            String difficulty,
            Double minRating,
            String sort,
            Pageable pageable) {

        Specification<Course> spec = Specification.where(publishedOnly());

        if (query != null && !query.isBlank()) {
            spec = spec.and(titleOrDescriptionContains(query));
        }
        if (difficulty != null && !difficulty.isBlank()) {
            try {
                Course.Difficulty d = Course.Difficulty.valueOf(difficulty.toUpperCase());
                spec = spec.and(hasDifficulty(d));
            } catch (IllegalArgumentException ignored) {
                log.warn("Unknown difficulty filter: {}", difficulty);
            }
        }

        Page<Course> results = courseRepository.findAll(spec, pageable);

        Map<String, Object> response = new HashMap<>();
        response.put("content", results.getContent());
        response.put("totalElements", results.getTotalElements());
        response.put("totalPages", results.getTotalPages());
        response.put("page", results.getNumber());
        response.put("size", results.getSize());
        return response;
    }

    public List<String> autocomplete(String query) {
        if (query == null || query.isBlank()) return List.of();
        Specification<Course> spec = Specification.where(publishedOnly())
                .and(titleStartsWith(query));
        return courseRepository.findAll(spec, Pageable.ofSize(10))
                .stream()
                .map(Course::getTitle)
                .collect(Collectors.toList());
    }

    // ─── JPA Specifications ──────────────────────────────────────────────────
    private Specification<Course> publishedOnly() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), Course.CourseStatus.PUBLISHED),
                cb.isFalse(root.get("archived"))
        );
    }

    private Specification<Course> titleOrDescriptionContains(String search) {
        String pattern = "%" + search.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern)
        );
    }

    private Specification<Course> titleStartsWith(String prefix) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), prefix.toLowerCase() + "%");
    }

    private Specification<Course> hasDifficulty(Course.Difficulty d) {
        return (root, query, cb) -> cb.equal(root.get("difficulty"), d);
    }
}
