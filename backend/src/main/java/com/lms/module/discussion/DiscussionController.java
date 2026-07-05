package com.lms.module.discussion;

import com.lms.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Discussions", description = "Lesson discussion forums")
public class DiscussionController {

    private final DiscussionThreadRepository threadRepository;

    @GetMapping("/lessons/{lessonId}/discussions")
    @Operation(summary = "List discussion threads for a lesson")
    public ResponseEntity<ApiResponse<Page<DiscussionThread>>> listThreads(
            @PathVariable UUID lessonId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<DiscussionThread> threads = threadRepository
                .findByLessonIdOrderByPinnedDescCreatedAtDesc(lessonId, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.success(threads));
    }

    @PostMapping("/lessons/{lessonId}/discussions")
    @Operation(summary = "Create a discussion thread")
    public ResponseEntity<ApiResponse<DiscussionThread>> createThread(
            @PathVariable UUID lessonId,
            @RequestBody CreateThreadRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID authorId = UUID.fromString(jwt.getSubject());
        DiscussionThread thread = DiscussionThread.builder()
                .lessonId(lessonId)
                .authorId(authorId)
                .title(request.title())
                .content(request.content())
                .pinned(false)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Thread created", threadRepository.save(thread)));
    }

    @PutMapping("/discussions/{id}/pin")
    @Operation(summary = "Pin/unpin a discussion thread (instructor/admin)")
    @Transactional
    public ResponseEntity<ApiResponse<DiscussionThread>> togglePin(@PathVariable UUID id) {
        DiscussionThread thread = threadRepository.findById(id)
                .orElseThrow(() -> new com.lms.common.exception.ResourceNotFoundException("Thread not found"));
        thread.setPinned(!thread.isPinned());
        return ResponseEntity.ok(ApiResponse.success(threadRepository.save(thread)));
    }

    public record CreateThreadRequest(String title, String content) {}
}
