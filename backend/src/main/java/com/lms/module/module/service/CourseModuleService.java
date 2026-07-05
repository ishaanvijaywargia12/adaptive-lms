package com.lms.module.module.service;

import com.lms.common.exception.ResourceNotFoundException;
import com.lms.module.module.entity.CourseModule;
import com.lms.module.module.repository.CourseModuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseModuleService {

    private final CourseModuleRepository moduleRepository;

    @Transactional
    public CourseModule createModule(UUID courseId, CreateModuleRequest request) {
        CourseModule module = CourseModule.builder()
                .courseId(courseId)
                .title(request.title())
                .orderIndex(request.orderIndex())
                .published(false)
                .build();
        return moduleRepository.save(module);
    }

    public List<CourseModule> getModulesForCourse(UUID courseId) {
        return moduleRepository.findByCourseIdOrderByOrderIndex(courseId);
    }

    @Transactional
    public CourseModule updateModule(UUID moduleId, UpdateModuleRequest request) {
        CourseModule module = moduleRepository.findById(moduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + moduleId));
        if (request.title() != null) module.setTitle(request.title());
        if (request.orderIndex() != null) module.setOrderIndex(request.orderIndex());
        if (request.published() != null) module.setPublished(request.published());
        return moduleRepository.save(module);
    }

    @Transactional
    public void deleteModule(UUID moduleId) {
        if (!moduleRepository.existsById(moduleId)) {
            throw new ResourceNotFoundException("Module not found: " + moduleId);
        }
        moduleRepository.deleteById(moduleId);
    }

    public record CreateModuleRequest(String title, int orderIndex) {}
    public record UpdateModuleRequest(String title, Integer orderIndex, Boolean published) {}
}
