package com.lms.module.ai.repository;

import com.lms.module.ai.entity.RagDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RagDocumentRepository extends JpaRepository<RagDocument, UUID> {
    List<RagDocument> findByCourseId(UUID courseId);
    Optional<RagDocument> findByCourseIdAndObjectKey(UUID courseId, String objectKey);
    boolean existsByCourseIdAndObjectKey(UUID courseId, String objectKey);
    void deleteByCourseIdAndObjectKey(UUID courseId, String objectKey);
}
