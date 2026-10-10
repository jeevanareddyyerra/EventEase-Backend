package com.eventease.dto.response;

import com.eventease.entity.DocumentType;

public class AiCitationResponse {

    private Long eventId;
    private String eventTitle;
    private Long documentId;
    private String documentTitle;
    private DocumentType documentType;
    private String chunkId;
    private double similarityScore;

    public AiCitationResponse() {
    }

    public AiCitationResponse(Long eventId, String eventTitle, Long documentId,
                              String documentTitle, DocumentType documentType,
                              String chunkId, double similarityScore) {
        this.eventId = eventId;
        this.eventTitle = eventTitle;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.documentType = documentType;
        this.chunkId = chunkId;
        this.similarityScore = similarityScore;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getEventTitle() {
        return eventTitle;
    }

    public void setEventTitle(String eventTitle) {
        this.eventTitle = eventTitle;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getChunkId() {
        return chunkId;
    }

    public void setChunkId(String chunkId) {
        this.chunkId = chunkId;
    }

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }
}
