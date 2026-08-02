package com.lms.module.ai.service;

import com.lms.common.service.MinioStorageService;
import com.lms.kafka.event.RagDocumentIngestionEvent;
import com.lms.module.ai.entity.RagDocument;
import com.lms.module.ai.repository.QdrantVectorRepository;
import com.lms.module.ai.repository.RagDocumentRepository;
import com.lms.tenant.TenantContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full PDF/document-to-vector ingestion pipeline:
 * <ol>
 *   <li>Download document bytes from MinIO</li>
 *   <li>Extract plain text via Apache Tika</li>
 *   <li>Split into overlapping chunks (sliding window)</li>
 *   <li>Generate 384-dim embeddings via AllMiniLmL6V2 (LangChain4j)</li>
 *   <li>Clean up any stale chunks for this objectKey in Qdrant</li>
 *   <li>Upsert each chunk + embedding into Qdrant with tenant-aware payload</li>
 *   <li>Update {@link RagDocument} lifecycle status in the database</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    @Value("${rag.chunk-size:1000}")
    private int chunkSize;

    @Value("${rag.chunk-overlap:200}")
    private int chunkOverlap;

    private final MinioClient minioClient;
    private final MinioStorageService minioStorageService;
    private final EmbeddingModel embeddingModel;
    private final QdrantVectorRepository qdrantVectorRepository;
    private final RagDocumentRepository ragDocumentRepository;

    private static final Tika TIKA = new Tika();

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Processes the document referenced by {@code event.minioKey} and ingests all
     * chunks into Qdrant under the event's tenantId + courseId namespace.
     */
    @Transactional
    public void ingest(RagDocumentIngestionEvent event) {
        String tenantId = event.getTenantId();
        TenantContext.setCurrentTenant(tenantId);

        RagDocument ragDoc = ragDocumentRepository.findByCourseIdAndObjectKey(event.getCourseId(), event.getMinioKey())
                .orElseGet(() -> RagDocument.builder()
                        .courseId(event.getCourseId())
                        .objectKey(event.getMinioKey())
                        .filename(extractFilename(event.getMinioKey()))
                        .status(RagDocument.Status.UPLOADED)
                        .build());

        ragDoc.setStatus(RagDocument.Status.INDEXING);
        ragDoc.setErrorMessage(null);
        ragDocumentRepository.save(ragDoc);

        try {
            log.info("[INGEST] Downloading document from MinIO: key={}", event.getMinioKey());
            byte[] docBytes = downloadFromMinio(event.getMinioKey());
            ragDoc.setFileSizeBytes((long) docBytes.length);

            log.info("[INGEST] Extracting text via Tika ({} bytes)", docBytes.length);
            String rawText = extractText(docBytes);

            if (rawText == null || rawText.isBlank()) {
                log.warn("[INGEST] Tika extracted empty text for key={}.", event.getMinioKey());
                ragDoc.setStatus(RagDocument.Status.FAILED);
                ragDoc.setErrorMessage("Document produced empty text content");
                ragDocumentRepository.save(ragDoc);
                return;
            }

            List<String> chunks = chunkText(rawText);
            log.info("[INGEST] Created {} chunks (size={}, overlap={}) for courseId={}",
                    chunks.size(), chunkSize, chunkOverlap, event.getCourseId());

            // Delete stale chunks for this objectKey before upserting new ones
            qdrantVectorRepository.deleteByObjectKey(tenantId, event.getCourseId(), event.getMinioKey());

            int upserted = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                if (chunk.isBlank()) continue;

                // LangChain4j AllMiniLmL6V2 → 384-dim float vector
                Embedding embedding = embeddingModel.embed(chunk).content();

                qdrantVectorRepository.upsert(
                        tenantId,
                        event.getCourseId(),
                        event.getMinioKey(),
                        i,
                        chunk,
                        embedding.vectorAsList()
                );
                upserted++;
            }

            ragDoc.setChunkCount(upserted);
            ragDoc.setStatus(RagDocument.Status.INDEXED);
            ragDoc.setIndexedAt(LocalDateTime.now());
            ragDocumentRepository.save(ragDoc);

            log.info("[INGEST] Successfully indexed {}/{} chunks for courseId={} key={}",
                    upserted, chunks.size(), event.getCourseId(), event.getMinioKey());

        } catch (Exception e) {
            log.error("[INGEST] Ingestion failed for courseId={} key={}: {}",
                    event.getCourseId(), event.getMinioKey(), e.getMessage(), e);
            ragDoc.setStatus(RagDocument.Status.FAILED);
            ragDoc.setErrorMessage(e.getMessage());
            ragDocumentRepository.save(ragDoc);
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private byte[] downloadFromMinio(String objectKey) throws Exception {
        String bucket = minioStorageService.getContentBucket();
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build())) {
            return stream.readAllBytes();
        }
    }

    private String extractText(byte[] bytes) throws Exception {
        return TIKA.parseToString(new ByteArrayInputStream(bytes));
    }

    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) end = lastSpace;
            }
            chunks.add(text.substring(start, end).strip());
            start += (chunkSize - chunkOverlap);
        }
        return chunks;
    }

    private String extractFilename(String objectKey) {
        if (objectKey == null) return "document";
        int lastSlash = objectKey.lastIndexOf('/');
        return lastSlash >= 0 ? objectKey.substring(lastSlash + 1) : objectKey;
    }
}
