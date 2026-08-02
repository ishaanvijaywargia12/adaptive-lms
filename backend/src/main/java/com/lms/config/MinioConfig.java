package com.lms.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO (S3-compatible) client configuration.
 *
 * <p>When {@code minio.url=disabled} (set in the demo profile when no object
 * storage is configured), a no-op stub is returned instead of a real client.
 * This prevents null-pointer exceptions in services that auto-wire {@link MinioClient}.
 *
 * <p>Services that rely on MinIO should handle {@link io.minio.errors.MinioException}
 * and fall back gracefully (e.g. base64 encoding for certificates in demo mode).
 */
@Configuration
@Slf4j
public class MinioConfig {

    @Value("${minio.url:disabled}")
    private String minioUrl;

    @Value("${minio.access-key:disabled}")
    private String accessKey;

    @Value("${minio.secret-key:disabled}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        if ("disabled".equalsIgnoreCase(minioUrl)) {
            log.warn("[MINIO] minio.url=disabled — object storage is not configured. " +
                     "File uploads will fall back to base64 in-DB storage.");
            // Return a client pointed at localhost — it will fail when used,
            // and callers must handle the exception and fall back gracefully.
            // We do NOT return null to avoid NullPointerExceptions in @Autowired fields.
            return MinioClient.builder()
                    .endpoint("http://localhost:9000")
                    .credentials("disabled", "disabled")
                    .build();
        }

        log.info("[MINIO] Connecting to {}", minioUrl);
        return MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();
    }
}
