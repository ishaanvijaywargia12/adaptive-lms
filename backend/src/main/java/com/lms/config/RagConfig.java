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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG (Retrieval-Augmented Generation) configuration.
 * <p>
 * Registers:
 * <ul>
 *   <li>{@link EmbeddingModel} — Local AllMiniLmL6V2 (384-dimensional output)</li>
 *   <li>{@link ChatLanguageModel} — Google Gemini 1.5 Flash</li>
 *   <li>{@link QdrantClient} — gRPC client; auto-creates collection + payload indexes</li>
 * </ul>
 *
 * <p>Supports both local Qdrant (plain TCP) and Qdrant Cloud (TLS + API key).
 * Set {@code qdrant.use-tls=true} and {@code qdrant.api-key=<key>} for cloud.
 */
@Configuration
@Slf4j
public class RagConfig {

    /** Single shared Qdrant collection. Tenant isolation enforced via payload filters. */
    public static final String QDRANT_COLLECTION = "lms_course_chunks";

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${qdrant.host:localhost}")
    private String qdrantHost;

    @Value("${qdrant.port:6334}")
    private int qdrantPort;

    /**
     * For Qdrant Cloud: set to API key from cloud.qdrant.io.
     * Leave empty for local Docker instance.
     */
    @Value("${qdrant.api-key:}")
    private String qdrantApiKey;

    /**
     * Set to {@code true} for Qdrant Cloud (requires TLS).
     * {@code false} for local Docker.
     */
    @Value("${qdrant.use-tls:false}")
    private boolean qdrantUseTls;

    @Value("${qdrant.collection.vector-size:384}")
    private int vectorSize;

    // ─── LangChain4j Beans ────────────────────────────────────────────────────

    /**
     * Local embedding model using AllMiniLmL6V2.
     * Produces 384-dimensional float vectors — matches {@code vector-size: 384} in config.
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("[RAG] Loading AllMiniLmL6V2 local embedding model (384 dims)...");
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /**
     * Google Gemini chat model (gemini-1.5-flash).
     * Returns a no-op model if API key is missing (graceful degradation for demo without key).
     */
    @Bean
    public ChatLanguageModel chatModel() {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("[RAG] GEMINI_API_KEY is not set. RAG doubt answering will return an error message.");
            // Return a stub model that returns a clear error message
            return userMessage -> dev.langchain4j.model.output.Response.from(
                    dev.langchain4j.data.message.AiMessage.from(
                            "AI answering is currently unavailable (API key not configured)."
                    )
            );
        }
        log.info("[RAG] Initializing Gemini 1.5 Flash chat model.");
        return GoogleAiGeminiChatModel.builder()
                .apiKey(geminiApiKey)
                .modelName("gemini-1.5-flash")
                .temperature(0.2)
                .maxRetries(3)
                .build();
    }

    // ─── Qdrant Client Bean ───────────────────────────────────────────────────

    /**
     * Qdrant gRPC client.
     * <ul>
     *   <li>Local: plain TCP, no auth</li>
     *   <li>Qdrant Cloud: TLS enabled, API key in metadata</li>
     * </ul>
     * On startup, ensures the collection exists and has keyword payload indexes
     * on {@code tenantId} and {@code courseId} for efficient filtered searches.
     */
    @Bean
    public QdrantClient qdrantClient() throws Exception {
        if ("disabled".equals(qdrantHost)) {
            log.warn("[QDRANT] qdrant.host=disabled — RAG vector search is disabled in this profile.");
            return null; // Components consuming QdrantClient must handle null gracefully
        }

        log.info("[QDRANT] Connecting to {}:{} tls={}", qdrantHost, qdrantPort, qdrantUseTls);

        QdrantGrpcClient.Builder grpcBuilder = QdrantGrpcClient.newBuilder(qdrantHost, qdrantPort, qdrantUseTls);

        // Add API key for Qdrant Cloud
        if (qdrantApiKey != null && !qdrantApiKey.isBlank()) {
            grpcBuilder.withApiKey(qdrantApiKey);
        }

        QdrantClient client = new QdrantClient(grpcBuilder.build());

        try {
            ensureCollectionExists(client);
        } catch (Exception e) {
            log.error("[QDRANT] Collection initialization failed: {}. RAG will be degraded.", e.getMessage());
            // Don't crash startup — return the client anyway
        }
        return client;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void ensureCollectionExists(QdrantClient client) throws Exception {
        boolean exists = false;
        try {
            client.getCollectionInfoAsync(QDRANT_COLLECTION).get();
            log.info("[QDRANT] Collection '{}' already exists.", QDRANT_COLLECTION);
            exists = true;
        } catch (Exception e) {
            log.info("[QDRANT] Collection '{}' not found — creating...", QDRANT_COLLECTION);
        }

        if (exists) return;

        client.createCollectionAsync(
                QDRANT_COLLECTION,
                VectorParams.newBuilder()
                        .setSize(vectorSize)
                        .setDistance(Distance.Cosine)
                        .build()
        ).get();

        // Payload indexes for O(log n) filtered vector searches
        client.createPayloadIndexAsync(
                QDRANT_COLLECTION, "tenantId",
                PayloadSchemaType.Keyword, null, null, null, null
        ).get();

        client.createPayloadIndexAsync(
                QDRANT_COLLECTION, "courseId",
                PayloadSchemaType.Keyword, null, null, null, null
        ).get();

        client.createPayloadIndexAsync(
                QDRANT_COLLECTION, "objectKey",
                PayloadSchemaType.Keyword, null, null, null, null
        ).get();

        log.info("[QDRANT] Collection '{}' created with tenantId/courseId/objectKey indexes.", QDRANT_COLLECTION);
    }
}
