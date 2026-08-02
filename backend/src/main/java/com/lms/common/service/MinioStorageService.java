package com.lms.common.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper service for all MinIO operations:
 * - Presigned URL generation (for direct browser uploads)
 * - Object upload (for server-side uploads like certificates)
 * - Object deletion
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService {

    private final MinioClient minioClient;

    @Value("${minio.buckets.public}")
    private String publicBucket;

    @Value("${minio.buckets.content}")
    private String contentBucket;

    @Value("${minio.buckets.submissions}")
    private String submissionsBucket;

    @Value("${minio.buckets.certificates}")
    private String certificatesBucket;

    /**
     * Get a presigned PUT URL for direct client upload (video, PDF, image).
     * Expires in 15 minutes.
     */
    public String getPresignedUploadUrl(String bucket, String objectKey) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(15, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new RuntimeException("Could not generate upload URL", e);
        }
    }

    /**
     * Get presigned GET URL for downloading/streaming content.
     */
    public String getPresignedDownloadUrl(String bucket, String objectKey) {
        return generatePresignedUrl(bucket, objectKey, 900); // 15 minutes
    }

    /**
     * Generate a presigned GET URL with a specific expiry (in seconds).
     */
    public String generatePresignedUrl(String bucket, String objectKey, int expirySeconds) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(expirySeconds, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new RuntimeException("Could not generate download URL", e);
        }
    }

    /**
     * Upload bytes directly to MinIO (used for server-generated content like certificates).
     * Returns the public URL of the uploaded object.
     */
    public String uploadBytes(String bucket, String objectKey, byte[] data, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
            return objectKey; // Return object key; full URL built by caller
        } catch (Exception e) {
            log.error("Failed to upload to MinIO {}/{}: {}", bucket, objectKey, e.getMessage());
            throw new RuntimeException("MinIO upload failed", e);
        }
    }

    public String uploadCertificate(String tenantId, String studentId, String courseId, byte[] pdfBytes) {
        String key = tenantId + "/" + studentId + "/" + courseId + "/certificate.pdf";
        uploadBytes(certificatesBucket, key, pdfBytes, "application/pdf");
        return key;
    }

    public String uploadQrCode(String tenantId, String studentId, String courseId, byte[] pngBytes) {
        String key = tenantId + "/" + studentId + "/" + courseId + "/qr.png";
        uploadBytes(certificatesBucket, key, pngBytes, "image/png");
        return key;
    }

    public String getLessonContentUrl(String tenantId, String lessonId, String filename) {
        String key = "videos/" + tenantId + "/" + lessonId + "/" + filename;
        return getPresignedUploadUrl(contentBucket, key);
    }

    public String getContentBucket() { return contentBucket; }
    public String getPublicBucket() { return publicBucket; }
    public String getSubmissionsBucket() { return submissionsBucket; }
    public String getCertificatesBucket() { return certificatesBucket; }
}
