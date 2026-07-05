package com.lms.module.ai.repository;

import com.lms.config.RagConfig;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points.*;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.ValueFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Repository wrapper around the Qdrant gRPC client.
 * <p>
 * All operations enforce tenant isolation via Qdrant payload filters on
 * {@code tenantId} and {@code courseId}. No cross-tenant data is ever returned.
 *
 * <p><b>Collection schema per point:</b>
 * <pre>
 * {
 *   id:       UUID (deterministic: SHA of tenantId+courseId+chunkIndex),
 *   vector:   float[1536]   (text-embedding-ada-002),
 *   payload: {
 *     tenantId:   string,
 *     courseId:   string,
 *     minioKey:   string,
 *     chunkIndex: int,
 *     text:       string    // raw chunk for LLM context injection
 *   }
 * }
 * </pre>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class QdrantVectorRepository {

    private final QdrantClient qdrantClient;

    // ─── Write ────────────────────────────────────────────────────────────────

    /**
     * Upserts a single embedded chunk into Qdrant with full tenant-aware metadata.
     *
     * @param tenantId   Tenant schema identifier (used as payload filter key)
     * @param courseId   Course UUID (used as payload filter key)
     * @param minioKey   Object key of the source PDF in MinIO (for traceability)
     * @param chunkIndex Zero-based chunk ordinal within the document
     * @param chunkText  Raw text content of this chunk
     * @param vector     1536-dimension embedding float list
     */
    public void upsert(String tenantId, UUID courseId, String minioKey,
                       int chunkIndex, String chunkText, List<Float> vector) {

        // Deterministic point ID: same chunk always maps to same Qdrant point.
        // Re-indexing the same PDF is idempotent (upsert overwrites).
        UUID pointId = UUID.nameUUIDFromBytes(
                (tenantId + "|" + courseId + "|" + chunkIndex).getBytes()
        );

        PointStruct point = PointStruct.newBuilder()
                .setId(PointIdFactory.id(pointId))
                .setVectors(VectorsFactory.vectors(vector))
                .putAllPayload(Map.of(
                        "tenantId",   ValueFactory.value(tenantId),
                        "courseId",   ValueFactory.value(courseId.toString()),
                        "minioKey",   ValueFactory.value(minioKey),
                        "chunkIndex", ValueFactory.value(chunkIndex),
                        "text",       ValueFactory.value(chunkText)
                ))
                .build();

        try {
            qdrantClient.upsertAsync(RagConfig.QDRANT_COLLECTION, List.of(point)).get();
            log.debug("[QDRANT] Upserted chunk {}/{} for tenantId={} courseId={}", chunkIndex, "(n)", tenantId, courseId);
        } catch (Exception e) {
            log.error("[QDRANT] Upsert failed tenantId={} courseId={} chunk={}: {}",
                    tenantId, courseId, chunkIndex, e.getMessage());
            throw new RuntimeException("Qdrant upsert failed for chunk " + chunkIndex, e);
        }
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * Searches Qdrant for the top-K most semantically similar chunks, strictly
     * filtered to the given tenant and course.
     *
     * <p>Dual {@code must} filters guarantee zero cross-tenant data leakage:
     * <ul>
     *   <li>{@code tenantId} must match the current request tenant</li>
     *   <li>{@code courseId} must match the queried course</li>
     * </ul>
     *
     * @param tenantId    Tenant to search within
     * @param courseId    Course whose chunks to search
     * @param queryVector Embedding of the student's question (1536 dims)
     * @param topK        Maximum number of chunks to return (typically 5)
     * @return Ordered list of raw chunk texts (highest cosine similarity first)
     */
    public List<String> searchTopK(String tenantId, UUID courseId,
                                   List<Float> queryVector, int topK) {

        Filter tenantCourseFilter = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("tenantId")
                                .setMatch(Match.newBuilder().setKeyword(tenantId).build())
                                .build()))
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("courseId")
                                .setMatch(Match.newBuilder().setKeyword(courseId.toString()).build())
                                .build()))
                .build();

        SearchPoints searchRequest = SearchPoints.newBuilder()
                .setCollectionName(RagConfig.QDRANT_COLLECTION)
                .addAllVector(queryVector)
                .setFilter(tenantCourseFilter)
                .setLimit(topK)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                .build();

        try {
            List<ScoredPoint> results = qdrantClient.searchAsync(searchRequest).get();
            log.debug("[QDRANT] Found {} chunks for tenantId={} courseId={}", results.size(), tenantId, courseId);
            return results.stream()
                    .map(p -> p.getPayloadMap().get("text").getStringValue())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[QDRANT] Search failed tenantId={} courseId={}: {}", tenantId, courseId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Deletes all Qdrant vectors for a specific course (e.g. when course is archived).
     */
    public void deleteByTenantAndCourse(String tenantId, UUID courseId) {
        Filter filter = Filter.newBuilder()
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("tenantId")
                                .setMatch(Match.newBuilder().setKeyword(tenantId).build())
                                .build()))
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("courseId")
                                .setMatch(Match.newBuilder().setKeyword(courseId.toString()).build())
                                .build()))
                .build();

        try {
            qdrantClient.deleteAsync(
                    RagConfig.QDRANT_COLLECTION,
                    Filter.newBuilder(filter).build()
            ).get();
            log.info("[QDRANT] Deleted all vectors for tenantId={} courseId={}", tenantId, courseId);
        } catch (Exception e) {
            log.error("[QDRANT] Delete failed tenantId={} courseId={}: {}", tenantId, courseId, e.getMessage());
        }
    }
}
