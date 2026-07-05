package com.lms.module.discussion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DiscussionThreadRepository extends JpaRepository<DiscussionThread, UUID> {
    Page<DiscussionThread> findByLessonIdOrderByPinnedDescCreatedAtDesc(UUID lessonId, Pageable pageable);
}
