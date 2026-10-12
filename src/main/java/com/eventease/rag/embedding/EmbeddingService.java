package com.eventease.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Service for local, in-process embedding generation and vector similarity computation.
 * Uses an ONNX-quantized all-MiniLM-L6-v2 model running natively in Java 21 without external network calls.
 * Model initialization is deferred so that embedded web server startup (Tomcat port binding) is not blocked.
 */
@Service
@Lazy
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    public static final int EMBEDDING_DIMENSION = 384;

    private volatile EmbeddingModel embeddingModel;

    public EmbeddingService() {
        // Model initialization is deferred to avoid blocking Spring Boot application startup
    }

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        if (embeddingModel != null) {
            log.info("Initialized local EmbeddingService with provided model, dimension {}", EMBEDDING_DIMENSION);
        }
    }

    public EmbeddingModel getOrInitModel() {
        if (embeddingModel == null) {
            synchronized (this) {
                if (embeddingModel == null) {
                    log.info("Initializing in-process ONNX EmbeddingModel (all-MiniLM-L6-v2-q)...");
                    this.embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
                    log.info("Initialized local EmbeddingService with dimension {}", EMBEDDING_DIMENSION);
                }
            }
        }
        return embeddingModel;
    }

    /**
     * Generates a 384-dimensional dense embedding vector for the provided text.
     *
     * @param text The input text string to embed
     * @return float array representing the embedding vector, or empty array if text is null/blank
     */
    public float[] embedText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[0];
        }

        try {
            Response<Embedding> response = getOrInitModel().embed(text.trim());
            Embedding embedding = response.content();
            float[] vector = embedding.vector();

            if (vector == null || vector.length == 0) {
                log.warn("Embedding model returned empty vector for input");
                return new float[0];
            }

            return vector;
        } catch (Exception e) {
            log.error("Failed to generate embedding for input text: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate embedding", e);
        }
    }

    /**
     * Generates embedding vectors for a batch of text chunks.
     *
     * @param texts List of input strings
     * @return List of float array embeddings corresponding to the input list
     */
    public List<float[]> embedAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }

        List<float[]> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embedText(text));
        }
        return results;
    }

    /**
     * Computes the cosine similarity between two float vectors.
     *
     * @param vectorA First vector
     * @param vectorB Second vector
     * @return Cosine similarity in range [-1.0, 1.0], or 0.0 if either vector is null/empty or zero-magnitude
     */
    public double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length == 0 || vectorB.length == 0) {
            return 0.0;
        }

        if (vectorA.length != vectorB.length) {
            log.warn("Vector dimension mismatch: vectorA ({}) vs vectorB ({})", vectorA.length, vectorB.length);
            return 0.0;
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += (double) vectorA[i] * vectorB[i];
            normA += (double) vectorA[i] * vectorA[i];
            normB += (double) vectorB[i] * vectorB[i];
        }

        if (normA <= 0.0 || normB <= 0.0) {
            return 0.0;
        }

        double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        // Bound result between -1.0 and 1.0 to guard against floating point inaccuracies
        return Math.max(-1.0, Math.min(1.0, similarity));
    }

    /**
     * Expected dimension of embeddings produced by this service.
     */
    public int getDimension() {
        return EMBEDDING_DIMENSION;
    }
}
