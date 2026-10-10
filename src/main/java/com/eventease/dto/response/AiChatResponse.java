package com.eventease.dto.response;

import java.util.Collections;
import java.util.List;

public class AiChatResponse {

    private String answer;
    private boolean grounded;
    private boolean evidenceFound;
    private List<AiCitationResponse> sources;
    private double topSimilarityScore;
    private String modelUsed;
    private String fallbackReason;

    public AiChatResponse() {
        this.sources = Collections.emptyList();
    }

    public AiChatResponse(String answer, boolean grounded, boolean evidenceFound,
                          List<AiCitationResponse> sources, double topSimilarityScore,
                          String modelUsed, String fallbackReason) {
        this.answer = answer;
        this.grounded = grounded;
        this.evidenceFound = evidenceFound;
        this.sources = sources != null ? sources : Collections.emptyList();
        this.topSimilarityScore = topSimilarityScore;
        this.modelUsed = modelUsed;
        this.fallbackReason = fallbackReason;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public boolean isGrounded() {
        return grounded;
    }

    public void setGrounded(boolean grounded) {
        this.grounded = grounded;
    }

    public boolean isEvidenceFound() {
        return evidenceFound;
    }

    public void setEvidenceFound(boolean evidenceFound) {
        this.evidenceFound = evidenceFound;
    }

    public List<AiCitationResponse> getSources() {
        return sources;
    }

    public void setSources(List<AiCitationResponse> sources) {
        this.sources = sources != null ? sources : Collections.emptyList();
    }

    public double getTopSimilarityScore() {
        return topSimilarityScore;
    }

    public void setTopSimilarityScore(double topSimilarityScore) {
        this.topSimilarityScore = topSimilarityScore;
    }

    public String getModelUsed() {
        return modelUsed;
    }

    public void setModelUsed(String modelUsed) {
        this.modelUsed = modelUsed;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public void setFallbackReason(String fallbackReason) {
        this.fallbackReason = fallbackReason;
    }
}
