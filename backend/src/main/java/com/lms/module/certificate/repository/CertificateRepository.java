package com.lms.module.certificate.repository;

import com.lms.module.certificate.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, UUID> {
    Optional<Certificate> findByVerificationCode(UUID verificationCode);
    Optional<Certificate> findByStudentIdAndCourseId(UUID studentId, UUID courseId);
    List<Certificate> findByStudentId(UUID studentId);
    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);
}
