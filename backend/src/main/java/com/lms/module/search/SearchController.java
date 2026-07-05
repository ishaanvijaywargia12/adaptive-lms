package com.lms.module.search;

import com.lms.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Full-text course and lesson search")
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/courses")
    @Operation(summary = "Search courses with filters")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchCourses(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                searchService.searchCourses(q, category, difficulty, minRating, sort, PageRequest.of(page, size))));
    }

    @GetMapping("/autocomplete")
    @Operation(summary = "Autocomplete course title suggestions")
    public ResponseEntity<ApiResponse<List<String>>> autocomplete(@RequestParam String q) {
        return ResponseEntity.ok(ApiResponse.success(searchService.autocomplete(q)));
    }
}
