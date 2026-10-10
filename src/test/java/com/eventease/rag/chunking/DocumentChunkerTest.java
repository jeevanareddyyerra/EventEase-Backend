package com.eventease.rag.chunking;

import com.eventease.rag.config.RagProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkerTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\t", "\n\n  \n"})
    @DisplayName("Should return empty list for null, empty, or whitespace-only content")
    void shouldReturnEmptyListForNullOrBlankContent(String input) {
        DocumentChunker chunker = new DocumentChunker();
        List<String> chunks = chunker.chunkText(input);

        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @Test
    @DisplayName("Should return single chunk when content is shorter than chunk size")
    void shouldReturnSingleChunkWhenContentShorterThanChunkSize() {
        DocumentChunker chunker = new DocumentChunker(500, 100);
        String text = "This is a short policy statement regarding event eligibility.";

        List<String> chunks = chunker.chunkText(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    @DisplayName("Should return single chunk when content is exactly equal to chunk size")
    void shouldReturnSingleChunkWhenContentEqualsChunkSize() {
        String text = "A".repeat(200);
        DocumentChunker chunker = new DocumentChunker(200, 50);

        List<String> chunks = chunker.chunkText(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    @DisplayName("Should split long text into overlapping chunks")
    void shouldSplitContentWithConfiguredOverlap() {
        // 350 chars text, chunkSize=150, overlap=30
        String text = "First paragraph discussing event rules in detail. " +
                "Second paragraph explaining participant requirements and dress code. " +
                "Third paragraph outlining the scoring rubric and submission deadlines for projects. " +
                "Final concluding sentence.";

        DocumentChunker chunker = new DocumentChunker(100, 25);
        List<String> chunks = chunker.chunkText(text);

        assertTrue(chunks.size() >= 3, "Expected at least 3 chunks, got: " + chunks.size());

        // Verify all chunks are non-empty and respect maximum size
        for (String chunk : chunks) {
            assertFalse(chunk.isBlank());
            assertTrue(chunk.length() <= 120, "Chunk exceeded maximum bounds: " + chunk.length());
        }
    }

    @Test
    @DisplayName("Should preserve sentence boundaries when practical rather than breaking mid-word")
    void shouldPreserveSentenceBoundariesWhenPractical() {
        String text = "Registration closes at midnight. All participants must present student ID. Late entries will be disqualified.";
        // Set chunk size to split roughly around the second sentence
        DocumentChunker chunker = new DocumentChunker(75, 20);

        List<String> chunks = chunker.chunkText(text);

        assertFalse(chunks.isEmpty());
        // First chunk should ideally end at a sentence period or space, not mid-word
        String firstChunk = chunks.get(0);
        assertTrue(firstChunk.endsWith(".") || firstChunk.endsWith("ID") || firstChunk.endsWith("midnight"),
                "Expected chunk to end at sentence boundary, got: " + firstChunk);
    }

    @Test
    @DisplayName("Should safely handle continuous unbroken text without infinite loop")
    void shouldHandleContinuousUnbrokenTextSafely() {
        String longUnbrokenString = "abcdefghijklmnopqrstuvwxyz".repeat(20); // 520 chars unbroken
        DocumentChunker chunker = new DocumentChunker(100, 20);

        List<String> chunks = chunker.chunkText(longUnbrokenString);

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.size() >= 5);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 100);
        }
    }

    @Test
    @DisplayName("Should reject invalid configuration parameters with descriptive IllegalArgumentException")
    void shouldRejectInvalidConfiguration() {
        // Zero chunk size
        assertThrows(IllegalArgumentException.class, () -> new DocumentChunker(0, 10));

        // Negative chunk size
        assertThrows(IllegalArgumentException.class, () -> new DocumentChunker(-50, 10));

        // Negative overlap
        assertThrows(IllegalArgumentException.class, () -> new DocumentChunker(100, -5));

        // Overlap equal to chunk size (would cause infinite loop)
        assertThrows(IllegalArgumentException.class, () -> new DocumentChunker(100, 100));

        // Overlap greater than chunk size
        assertThrows(IllegalArgumentException.class, () -> new DocumentChunker(100, 120));
    }

    @Test
    @DisplayName("Should load parameters from RagProperties properly")
    void shouldInitializeFromRagProperties() {
        RagProperties props = new RagProperties(300, 60, true);
        DocumentChunker chunker = new DocumentChunker(props);

        assertEquals(300, chunker.getDefaultChunkSize());
        assertEquals(60, chunker.getDefaultChunkOverlap());

        String text = "Sample text content that will be processed with custom properties.";
        List<String> chunks = chunker.chunkText(text);
        assertEquals(1, chunks.size());
    }

    @Test
    @DisplayName("Should use safe defaults when null RagProperties is provided")
    void shouldUseSafeDefaultsForNullProperties() {
        DocumentChunker chunker = new DocumentChunker(null);

        assertEquals(500, chunker.getDefaultChunkSize());
        assertEquals(100, chunker.getDefaultChunkOverlap());
    }
}
