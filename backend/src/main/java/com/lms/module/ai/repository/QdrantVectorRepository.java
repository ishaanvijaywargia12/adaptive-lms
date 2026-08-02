package com.lms.module.ai.repository;

import com.lms.config.RagConfig;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Points.*;
import lombok.Builder;
import lombok.Data;
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
 * <p><b>Point ID format:</b>
 * {@code UUID.nameUUIDFromBytes(tenantId|courseId|minioKey|chunkIndex)} — deterministic per file chunk.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class QdrantVectorRepository {

    private final QdrantClient qdrantClient;

    @Data
    @Builder
    public static class VectorChunkResult {
        private String text;
        private String objectKey;
        private int chunkIndex;
        private float score;
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    /**
     * Upserts a single embedded chunk into Qdrant with full tenant-aware metadata.
     * Includes minioKey in point ID calculation to prevent cross-document collisions.
     */
    public void upsert(String tenantId, UUID courseId, String minioKey,
                       int chunkIndex, String chunkText, List<Float> vector) {
        if (qdrantClient == null) {
            log.warn("[QDRANT] Client is null — skipping vector upsert.");
            return;
        }

        // Deterministic point ID including minioKey — prevents document overwrites
        UUID pointId = UUID.nameUUIDFromBytes(
                (tenantId + "|" + courseId + "|" + minioKey + "|" + chunkIndex).getBytes()
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
            log.debug("[QDRANT] Upserted chunk {} for tenantId={} courseId={} minioKey={}", chunkIndex, tenantId, courseId, minioKey);
        } catch (Exception e) {
            log.error("[QDRANT] Upsert failed tenantId={} courseId={} minioKey={} chunk={}: {}",
                    tenantId, courseId, minioKey, chunkIndex, e.getMessage());
            throw new RuntimeException("Qdrant upsert failed for chunk " + chunkIndex, e);
        }
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    /**
     * Searches Qdrant for top-K chunks and returns raw chunk text list (legacy helper).
     */
    public List<String> searchTopK(String tenantId, UUID courseId,
                                   List<Float> queryVector, int topK) {
        return searchTopKWithDetails(tenantId, courseId, queryVector, topK).stream()
                .map(VectorChunkResult::getText)
                .collect(Collectors.toList());
    }

    /**
     * Searches Qdrant for top-K chunks with source traceability (objectKey, chunkIndex, score).
     */
    public List<VectorChunkResult> searchTopKWithDetails(String tenantId, UUID courseId,
                                                          List<Float> queryVector, int topK) {
        if (qdrantClient == null) {
            log.warn("[QDRANT] Client is null — returning empty search results.");
            return List.of();
        }

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
                    .map(p -> {
                        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = p.getPayloadMap();
                        String text = payload.containsKey("text") ? payload.get("text").getStringValue() : "";
                        String key = payload.containsKey("minioKey") ? payload.get("minioKey").getStringValue() : "";
                        int idx = payload.containsKey("chunkIndex") ? (int) payload.get("chunkIndex").getIntegerValue() : 0;
                        return VectorChunkResult.builder()
                                .text(text)
                                .objectKey(key)
                                .chunkIndex(idx)
                                .score(p.getScore())
                                .build();
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[QDRANT] Search failed tenantId={} courseId={}: {}", tenantId, courseId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Deletes all vectors associated with a specific object key.
     */
    public void deleteByObjectKey(String tenantId, UUID courseId, String objectKey) {
        if (qdrantClient == null) return;

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
                .addMust(Condition.newBuilder()
                        .setField(FieldCondition.newBuilder()
                                .setKey("minioKey")
                                .setMatch(Match.newBuilder().setKeyword(objectKey).build())
                                .build()))
                .build();

        try {
            qdrantClient.deleteAsync(RagConfig.QDRANT_COLLECTION, filter).get();
            log.info("[QDRANT] Deleted vectors for tenantId={} courseId={} objectKey={}", tenantId, courseId, objectKey);
        } catch (Exception e) {
            log.error("[QDRANT] Delete failed for objectKey={}: {}", objectKey, e.getMessage());
        }
    }

    /**
     * Deletes all Qdrant vectors for a specific course.
     */
    public void deleteByTenantAndCourse(String tenantId, UUID courseId) {
        if (qdrantClient == null) return;

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
            qdrantClient.deleteAsync(RagConfig.QDRANT_COLLECTION, filter).get();
            log.info("[QDRANT] Deleted all vectors for tenantId={} courseId={}", tenantId, courseId);
        } catch (Exception e) {
            log.error("[QDRANT] Delete failed tenantId={} courseId={}: {}", tenantId, courseId, e.getMessage());
        }
    }
}
