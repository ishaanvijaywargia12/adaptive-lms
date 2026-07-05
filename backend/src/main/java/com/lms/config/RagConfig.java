package com.lms.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.PayloadSchemaType;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * RAG (Retrieval-Augmented Generation) configuration.
 * <p>
 * Registers:
 * <ul>
 *   <li>{@link EmbeddingModel} — Local AllMiniLmL6V2 (384 dims)</li>
 *   <li>{@link ChatLanguageModel} — Google gemini-1.5-flash</li>
 *   <li>{@link QdrantClient} — gRPC client; auto-creates collection + payload indexes on startup</li>
 * </ul>
 */
@Configuration
@Slf4j
public class RagConfig {

    /** Name of the single shared Qdrant collection. Tenant isolation is enforced via payload filters. */
    public static final String QDRANT_COLLECTION = "lms_course_chunks_gemini";

    @Value("${gemini.api-key}")
    private String geminiApiKey;

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    @Value("${qdrant.collection.vector-size:384}")
    private int vectorSize;

    // ─── LangChain4j Beans ────────────────────────────────────────────────────

    /**
     * Local embedding model using AllMiniLmL6V2.
     * Produces 384-dimensional float vectors.
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * Google Gemini chat model using gemini-1.5-flash.
     * Low temperature (0.2) for factual, deterministic answers.
     */
    @Bean
    public ChatLanguageModel chatModel() {
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-1.5-flash")
                .temperature(0.2)
                .maxRetries(3)
                .build();
    }

    // ─── Qdrant Client Bean ───────────────────────────────────────────────────

    /**
     * Qdrant gRPC client. On startup, ensures the collection exists and has
     * keyword payload indexes on {@code tenantId} and {@code courseId} for
     * O(log n) filtered vector searches.
     */
    @Bean
    public QdrantClient qdrantClient() throws Exception {
        QdrantClient client = new QdrantClient(
                QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, false).build()
        );
        ensureCollectionExists(client);
        return client;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void ensureCollectionExists(QdrantClient client) {
        try {
            client.getCollectionInfoAsync(QDRANT_COLLECTION).get();
            log.info("[QDRANT] Collection '{}' already exists.", QDRANT_COLLECTION);
        } catch (Exception e) {
            log.info("[QDRANT] Collection '{}' not found — creating...", QDRANT_COLLECTION);
            try {
                client.createCollectionAsync(
                        QDRANT_COLLECTION,
                        VectorParams.newBuilder()
                                .setSize(vectorSize)
                                .setDistance(Distance.Cosine)
                                .build()
                ).get();

                // Payload indexes for efficient tenant + course filtering
                client.createPayloadIndexAsync(
                        QDRANT_COLLECTION, "tenantId",
                        PayloadSchemaType.Keyword, null, null, null, null
                ).get();

                client.createPayloadIndexAsync(
                        QDRANT_COLLECTION, "courseId",
                        PayloadSchemaType.Keyword, null, null, null, null
                ).get();

                log.info("[QDRANT] Collection '{}' created with tenantId/courseId indexes.", QDRANT_COLLECTION);
            } catch (Exception createEx) {
                log.error("[QDRANT] Failed to create collection: {}", createEx.getMessage(), createEx);
                throw new RuntimeException("Failed to initialize Qdrant collection", createEx);
            }
        }
    }
}
