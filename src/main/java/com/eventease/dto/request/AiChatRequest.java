package com.eventease.dto.request;

import com.eventease.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public class AiChatRequest {

    @NotBlank(message = "Question is required and must not be blank")
    @Size(max = 1000, message = "Question must not exceed 1000 characters")
    private String question;

    @Positive(message = "Event ID must be a positive number")
    private Long eventId;

    private DocumentType documentType;

    public AiChatRequest() {
    }

    public AiChatRequest(String question) {
        this.question = question;
    }

    public AiChatRequest(String question, Long eventId) {
        this.question = question;
        this.eventId = eventId;
    }

    public AiChatRequest(String question, Long eventId, DocumentType documentType) {
        this.question = question;
        this.eventId = eventId;
        this.documentType = documentType;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }
}
