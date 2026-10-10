package com.eventease.rag.embedding;

import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EmbeddingServiceTest {

    @Test
    @DisplayName("Should initialize local quantized model and produce 384-dimensional embeddings")
    void shouldProduce384DimensionalEmbeddings() {
        EmbeddingService embeddingService = new EmbeddingService(new AllMiniLmL6V2QuantizedEmbeddingModel());

        assertEquals(384, embeddingService.getDimension());

        float[] embedding = embeddingService.embedText("EventEase is an event management platform.");

        assertNotNull(embedding);
        assertEquals(384, embedding.length);

        // Verify embedding contains non-zero floats
        boolean hasNonZero = false;
        for (float val : embedding) {
            if (val != 0.0f) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue(hasNonZero, "Embedding should have non-zero values");
    }

    @Test
    @DisplayName("Should compute correct cosine similarity between vectors")
    void shouldComputeCosineSimilarity() {
        EmbeddingService embeddingService = new EmbeddingService();

        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v3 = new float[]{0.0f, 1.0f, 0.0f};
        float[] v4 = new float[]{-1.0f, 0.0f, 0.0f};

        assertEquals(1.0, embeddingService.cosineSimilarity(v1, v2), 0.0001);
        assertEquals(0.0, embeddingService.cosineSimilarity(v1, v3), 0.0001);
        assertEquals(-1.0, embeddingService.cosineSimilarity(v1, v4), 0.0001);
    }

    @Test
    @DisplayName("Should demonstrate higher semantic similarity for related sentences than unrelated sentences")
    void shouldDemonstrateSemanticSimilarity() {
        EmbeddingService embeddingService = new EmbeddingService(new AllMiniLmL6V2QuantizedEmbeddingModel());

        float[] hackathon = embeddingService.embedText("Participants will build software applications in the hackathon.");
        float[] coding = embeddingService.embedText("Developers write code and program computers for the contest.");
        float[] cooking = embeddingService.embedText("Bake a chocolate cake with butter, eggs, sugar, and flour in the oven.");

        double similarityRelated = embeddingService.cosineSimilarity(hackathon, coding);
        double similarityUnrelated = embeddingService.cosineSimilarity(hackathon, cooking);

        assertTrue(similarityRelated > similarityUnrelated,
                String.format("Expected related similarity (%.4f) > unrelated similarity (%.4f)",
                        similarityRelated, similarityUnrelated));
        assertTrue(similarityRelated > 0.5, "Related text should have strong similarity");
    }

    @Test
    @DisplayName("Should return empty array for null or blank input text")
    void shouldHandleNullOrBlankText() {
        EmbeddingService embeddingService = new EmbeddingService();

        float[] resultNull = embeddingService.embedText(null);
        assertNotNull(resultNull);
        assertEquals(0, resultNull.length);

        float[] resultBlank = embeddingService.embedText("    ");
        assertNotNull(resultBlank);
        assertEquals(0, resultBlank.length);
    }

    @Test
    @DisplayName("Should embed batch of texts")
    void shouldEmbedBatch() {
        EmbeddingService embeddingService = new EmbeddingService();

        List<float[]> embeddings = embeddingService.embedAll(List.of("First phrase", "Second phrase"));
        assertEquals(2, embeddings.size());
        assertEquals(384, embeddings.get(0).length);
        assertEquals(384, embeddings.get(1).length);

        assertTrue(embeddingService.embedAll(null).isEmpty());
        assertTrue(embeddingService.embedAll(List.of()).isEmpty());
    }

    @Test
    @DisplayName("Should handle edge cases in cosine similarity calculation")
    void shouldHandleCosineSimilarityEdgeCases() {
        EmbeddingService embeddingService = new EmbeddingService();

        assertEquals(0.0, embeddingService.cosineSimilarity(null, new float[]{1.0f}));
        assertEquals(0.0, embeddingService.cosineSimilarity(new float[]{1.0f}, null));
        assertEquals(0.0, embeddingService.cosineSimilarity(new float[0], new float[]{1.0f}));
        // Dimension mismatch
        assertEquals(0.0, embeddingService.cosineSimilarity(new float[]{1.0f, 2.0f}, new float[]{1.0f}));
        // Zero magnitude vectors
        assertEquals(0.0, embeddingService.cosineSimilarity(new float[]{0.0f, 0.0f}, new float[]{0.0f, 0.0f}));
    }
}
