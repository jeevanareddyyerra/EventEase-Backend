package com.eventease.rag.chunking;

import com.eventease.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utility for breaking document text into overlapping, sentence-aware chunks.
 */
@Component
public class DocumentChunker {

    private final int defaultChunkSize;
    private final int defaultChunkOverlap;

    @org.springframework.beans.factory.annotation.Autowired
    public DocumentChunker(RagProperties ragProperties) {
        if (ragProperties != null) {
            validateConfig(ragProperties.getChunkSize(), ragProperties.getChunkOverlap());
            this.defaultChunkSize = ragProperties.getChunkSize();
            this.defaultChunkOverlap = ragProperties.getChunkOverlap();
        } else {
            this.defaultChunkSize = 500;
            this.defaultChunkOverlap = 100;
        }
    }

    public DocumentChunker(int chunkSize, int chunkOverlap) {
        validateConfig(chunkSize, chunkOverlap);
        this.defaultChunkSize = chunkSize;
        this.defaultChunkOverlap = chunkOverlap;
    }

    public DocumentChunker() {
        this(500, 100);
    }

    /**
     * Splits text content using the default configured chunk size and overlap.
     *
     * @param content Raw text content to chunk
     * @return List of clean text chunks
     */
    public List<String> chunkText(String content) {
        return chunkText(content, this.defaultChunkSize, this.defaultChunkOverlap);
    }

    /**
     * Splits text content using explicit chunk size and overlap parameters.
     *
     * @param content      Raw text content to chunk
     * @param chunkSize    Maximum target characters per chunk (> 0)
     * @param chunkOverlap Overlapping characters between adjacent chunks (>= 0 and < chunkSize)
     * @return List of clean text chunks
     */
    public List<String> chunkText(String content, int chunkSize, int chunkOverlap) {
        validateConfig(chunkSize, chunkOverlap);

        if (content == null || content.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String text = content.trim();
        int textLength = text.length();

        if (textLength <= chunkSize) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);

            if (end < textLength) {
                // Attempt to find a natural sentence boundary in the latter portion of the chunk
                int searchFrom = Math.max(start + (chunkSize / 2), end - Math.min(120, chunkSize / 3));
                int naturalBoundary = findNaturalBoundary(text, searchFrom, end);

                if (naturalBoundary > start) {
                    end = naturalBoundary;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end >= textLength) {
                break;
            }

            // Advance start position incorporating overlap
            int nextStart = end - chunkOverlap;
            // Guarantee strictly forward progress to avoid infinite loops
            if (nextStart <= start) {
                nextStart = start + 1;
            }

            // Skip leading whitespace at chunk start to keep text clean
            while (nextStart < textLength && Character.isWhitespace(text.charAt(nextStart))) {
                nextStart++;
            }

            start = nextStart;
        }

        return Collections.unmodifiableList(chunks);
    }

    /**
     * Searches for sentence terminators or word boundaries in the text slice.
     */
    private int findNaturalBoundary(String text, int searchFrom, int maxEnd) {
        int bestBoundary = -1;

        // 1. Look for sentence terminators followed by whitespace or newline
        for (int i = maxEnd - 1; i >= searchFrom; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && i + 1 < maxEnd && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
            if (c == '\n') {
                return i + 1;
            }
        }

        // 2. If no sentence boundary found, look for word boundary (space, semicolon, comma)
        for (int i = maxEnd - 1; i >= searchFrom; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || c == ',' || c == ';') {
                bestBoundary = i + 1;
                break;
            }
        }

        return bestBoundary;
    }

    private static void validateConfig(int chunkSize, int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than zero, got: " + chunkSize);
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap must not be negative, got: " + chunkOverlap);
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException(String.format(
                    "chunkOverlap (%d) must be strictly less than chunkSize (%d)", chunkOverlap, chunkSize));
        }
    }

    public int getDefaultChunkSize() {
        return defaultChunkSize;
    }

    public int getDefaultChunkOverlap() {
        return defaultChunkOverlap;
    }
}
