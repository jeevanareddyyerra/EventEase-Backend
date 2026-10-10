package com.eventease.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "eventease.rag")
public class RagProperties {

    /**
     * Target character length for document chunks. Default is 500 characters.
     */
    private int chunkSize = 500;

    /**
     * Character overlap between adjacent chunks. Default is 100 characters.
     */
    private int chunkOverlap = 100;

    /**
     * Whether to automatically index documents on application startup.
     */
    private boolean autoIndexOnStartup = true;

    /**
     * Minimum cosine similarity score required for retrieved chunks to be considered relevant.
     */
    private double similarityThreshold = 0.40;

    /**
     * Maximum number of chunks to return for semantic retrieval.
     */
    private int maxResults = 5;

    /**
     * Gemini AI Model configuration settings.
     */
    private GeminiProperties gemini = new GeminiProperties();

    public RagProperties() {
    }

    public RagProperties(int chunkSize, int chunkOverlap, boolean autoIndexOnStartup) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.autoIndexOnStartup = autoIndexOnStartup;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }

    public boolean isAutoIndexOnStartup() {
        return autoIndexOnStartup;
    }

    public void setAutoIndexOnStartup(boolean autoIndexOnStartup) {
        this.autoIndexOnStartup = autoIndexOnStartup;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public void setMaxResults(int maxResults) {
        this.maxResults = maxResults;
    }

    public GeminiProperties getGemini() {
        return gemini;
    }

    public void setGemini(GeminiProperties gemini) {
        this.gemini = gemini;
    }

    public static class GeminiProperties {
        private String apiKey = "";
        private String model = "gemini-3.8-flash";
        private int timeoutSeconds = 60;
        private int connectTimeoutSeconds = 10;
        private double temperature = 0.2;
        private boolean enabled = true;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
