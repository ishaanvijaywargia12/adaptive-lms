package com.lms.module.ai.service;

import com.lms.common.service.MinioStorageService;
import com.lms.kafka.event.RagDocumentIngestionEvent;
import com.lms.module.ai.repository.QdrantVectorRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the full PDF-to-vector ingestion pipeline:
 * <ol>
 *   <li>Download PDF bytes from MinIO</li>
 *   <li>Extract plain text via Apache Tika</li>
 *   <li>Split into overlapping chunks (sliding window)</li>
 *   <li>Generate 1536-dim embeddings via OpenAI text-embedding-ada-002 (LangChain4j)</li>
 *   <li>Upsert each chunk + embedding into Qdrant with tenant-aware payload</li>
 * </ol>
 *
 * <p>Re-ingesting the same PDF is safe: point IDs are deterministic (tenantId+courseId+chunkIndex),
 * so Qdrant simply overwrites existing points with updated vectors.
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

    private static final Tika TIKA = new Tika();

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Processes the PDF referenced by {@code event.minioKey} and ingests all
     * chunks into Qdrant under the event's tenantId + courseId namespace.
     *
     * @param event The Kafka ingestion event carrying tenantId, courseId, and minioKey
     * @throws Exception on MinIO download failure, Tika extraction error, or Qdrant upsert failure
     */
    public void ingest(RagDocumentIngestionEvent event) throws Exception {
        log.info("[INGEST] Downloading PDF from MinIO: key={}", event.getMinioKey());
        byte[] pdfBytes = downloadFromMinio(event.getMinioKey());

        log.info("[INGEST] Extracting text from PDF via Tika ({} bytes)", pdfBytes.length);
        String rawText = extractText(pdfBytes);

        if (rawText == null || rawText.isBlank()) {
            log.warn("[INGEST] Tika extracted empty text for key={}. Skipping.", event.getMinioKey());
            return;
        }

        List<String> chunks = chunkText(rawText);
        log.info("[INGEST] Created {} chunks (size={}, overlap={}) for courseId={}",
                chunks.size(), chunkSize, chunkOverlap, event.getCourseId());

        int upserted = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            if (chunk.isBlank()) continue;

            // LangChain4j call → OpenAI text-embedding-ada-002 → 1536-dim float vector
            Embedding embedding = embeddingModel.embed(chunk).content();

            qdrantVectorRepository.upsert(
                    event.getTenantId(),
                    event.getCourseId(),
                    event.getMinioKey(),
                    i,
                    chunk,
                    embedding.vectorAsList()
            );
            upserted++;
        }

        log.info("[INGEST] Successfully upserted {}/{} chunks into Qdrant for courseId={}",
                upserted, chunks.size(), event.getCourseId());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private byte[] downloadFromMinio(String objectKey) throws Exception {
        // Parse bucket from object key prefix (e.g. "lms-content/tenant/course/file.pdf")
        String bucket = minioStorageService.getContentBucket();
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build())) {
            return stream.readAllBytes();
        }
    }

    /**
     * Extracts plain text from any document format Apache Tika supports
     * (PDF, DOCX, PPTX, etc.).
     */
    private String extractText(byte[] bytes) throws Exception {
        return TIKA.parseToString(new ByteArrayInputStream(bytes));
    }

    /**
     * Splits text into overlapping chunks using a sliding window.
     * Overlap prevents losing context at chunk boundaries.
     *
     * <p>Word-boundary respect: steps back to the previous word boundary to
     * avoid splitting in the middle of a word.
     *
     * @param text Full extracted text
     * @return Ordered list of text chunks
     */
    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            // Avoid cutting mid-word if not at end
            if (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) end = lastSpace;
            }
            chunks.add(text.substring(start, end).strip());
            start += (chunkSize - chunkOverlap);
        }
        return chunks;
    }
}
