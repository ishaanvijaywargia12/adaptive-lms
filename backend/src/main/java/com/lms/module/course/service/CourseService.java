package com.lms.module.course.service;

import com.lms.common.exception.BusinessLogicException;
import com.lms.common.exception.ResourceNotFoundException;
import com.lms.kafka.event.BaseEvent;
import com.lms.kafka.producer.KafkaProducerService;
import com.lms.module.course.dto.CreateCourseRequest;
import com.lms.module.course.dto.UpdateCourseRequest;
import com.lms.module.course.entity.Course;
import com.lms.module.course.repository.CourseRepository;
import com.lms.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseService {

    private final CourseRepository courseRepository;
    private final KafkaProducerService kafkaProducer;

    @Transactional
    public Course createCourse(CreateCourseRequest request, UUID instructorId) {
        Course course = Course.builder()
                .title(request.title())
                .description(request.description())
                .instructorId(instructorId)
                .categoryId(request.categoryId())
                .difficulty(request.difficulty())
                .tags(request.tags())
                .price(request.price())
                .status(Course.CourseStatus.DRAFT)
                .build();
        return courseRepository.save(course);
    }

    @Transactional
    public Course updateCourse(UUID courseId, UpdateCourseRequest request, UUID userId) {
        Course course = getCourseById(courseId);
        if (!course.getInstructorId().equals(userId)) {
            throw new BusinessLogicException("You can only edit your own courses");
        }
        if (request.title() != null) course.setTitle(request.title());
        if (request.description() != null) course.setDescription(request.description());
        if (request.categoryId() != null) course.setCategoryId(request.categoryId());
        if (request.difficulty() != null) course.setDifficulty(request.difficulty());
        if (request.tags() != null) course.setTags(request.tags());
        if (request.price() != null) course.setPrice(request.price());
        return courseRepository.save(course);
    }

    public Course getCourseById(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course", id.toString()));
    }

    public java.util.List<Course> getCoursesByInstructor(UUID instructorId) {
        return courseRepository.findByInstructorIdOrderByCreatedAtDesc(instructorId);
    }

    public java.util.List<Course> getPendingCourses() {
        return courseRepository.findByStatus(Course.CourseStatus.UNDER_REVIEW);
    }

    @Transactional
    public Course rejectCourse(UUID courseId) {
        Course course = getCourseById(courseId);
        course.setStatus(Course.CourseStatus.REJECTED);
        return courseRepository.save(course);
    }

    /**
     * Dynamic filter using JPA Specifications — handles any combination of search, category, difficulty.
     */
    public Page<Course> searchCourses(String search, String category, String difficulty, Pageable pageable) {
        Specification<Course> spec = Specification.where(publishedOnly());

        if (search != null && !search.isBlank()) {
            spec = spec.and(titleContains(search));
        }
        if (difficulty != null && !difficulty.isBlank()) {
            try {
                Course.Difficulty d = Course.Difficulty.valueOf(difficulty.toUpperCase());
                spec = spec.and(hasDifficulty(d));
            } catch (IllegalArgumentException ignored) {}
        }

        return courseRepository.findAll(spec, pageable);
    }

    @Transactional
    public Course publishCourse(UUID courseId, UUID instructorId) {
        Course course = getCourseById(courseId);
        if (!course.getInstructorId().equals(instructorId)) {
            throw new BusinessLogicException("You can only publish your own courses");
        }
        if (course.getStatus() != Course.CourseStatus.DRAFT) {
            throw new BusinessLogicException("Only DRAFT courses can be submitted for review");
        }
        course.setStatus(Course.CourseStatus.UNDER_REVIEW);
        return courseRepository.save(course);
    }

    @Transactional
    public Course approveCourse(UUID courseId) {
        Course course = getCourseById(courseId);
        course.setStatus(Course.CourseStatus.PUBLISHED);
        Course saved = courseRepository.save(course);

        // Index in Elasticsearch via Kafka
        kafkaProducer.publishCoursePublished(new BaseEvent.CoursePublishedEvent(
                UUID.randomUUID().toString(),
                TenantContext.getCurrentTenant(),
                LocalDateTime.now(),
                course.getId(), course.getTitle(), course.getDescription(), course.getTags()
        ));

        return saved;
    }

    // ─── JPA Specifications ─────────────────────────────────────────────────
    private Specification<Course> publishedOnly() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), Course.CourseStatus.PUBLISHED),
                cb.isFalse(root.get("archived"))
        );
    }

    private Specification<Course> titleContains(String search) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + search.toLowerCase() + "%");
    }

    private Specification<Course> hasDifficulty(Course.Difficulty difficulty) {
        return (root, query, cb) -> cb.equal(root.get("difficulty"), difficulty);
    }
}
