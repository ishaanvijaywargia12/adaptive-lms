package com.lms.module.lesson.service;

import com.lms.common.exception.ResourceNotFoundException;
import com.lms.common.service.MinioStorageService;
import com.lms.module.lesson.entity.Lesson;
import com.lms.module.lesson.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LessonService {

    private final LessonRepository lessonRepository;
    private final MinioStorageService minioStorageService;

    @Transactional
    public Lesson createLesson(UUID moduleId, CreateLessonRequest request) {
        Lesson lesson = Lesson.builder()
                .moduleId(moduleId)
                .title(request.title())
                .contentType(Lesson.ContentType.valueOf(request.contentType()))
                .contentText(request.contentText())
                .orderIndex(request.orderIndex())
                .preview(request.preview())
                .build();
        return lessonRepository.save(lesson);
    }

    public List<Lesson> getLessonsForModule(UUID moduleId) {
        return lessonRepository.findByModuleIdOrderByOrderIndex(moduleId);
    }

    public Map<String, String> getUploadUrl(UUID lessonId, String filename, String contentType) {
        Lesson lesson = getLesson(lessonId);
        String objectName = buildObjectKey(lesson, filename);
        String uploadUrl = minioStorageService.getPresignedUploadUrl("lms-content", objectName);
        return Map.of("uploadUrl", uploadUrl, "objectKey", objectName);
    }

    @Transactional
    public Lesson confirmUpload(UUID lessonId, String objectKey) {
        Lesson lesson = getLesson(lessonId);
        String contentUrl = minioStorageService.getPresignedDownloadUrl("lms-content", objectKey);
        lesson.setContentUrl(contentUrl);
        return lessonRepository.save(lesson);
    }

    public Lesson getLesson(UUID lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found: " + lessonId));
    }

    @Transactional
    public void deleteLesson(UUID lessonId) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson not found: " + lessonId);
        }
        lessonRepository.deleteById(lessonId);
    }

    private String buildObjectKey(Lesson lesson, String filename) {
        String folder = switch (lesson.getContentType()) {
            case VIDEO -> "videos";
            case PDF -> "pdfs";
            default -> "misc";
        };
        return folder + "/" + lesson.getModuleId() + "/" + lesson.getId() + "/" + filename;
    }

    public record CreateLessonRequest(
            String title,
            String contentType,
            String contentText,
            int orderIndex,
            boolean preview
    ) {}
}
