package com.eventease.dto.request;

import com.eventease.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class EventDocumentRequest {

    private Long eventId;

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    private String title;

    @NotNull(message = "Document type is required")
    private DocumentType type;

    private String content;

    private String fileUrl;

    public EventDocumentRequest() {
    }

    public EventDocumentRequest(String title, DocumentType type, String content, String fileUrl) {
        this.title = title;
        this.type = type;
        this.content = content;
        this.fileUrl = fileUrl;
    }

    public EventDocumentRequest(Long eventId, String title, DocumentType type, String content, String fileUrl) {
        this.eventId = eventId;
        this.title = title;
        this.type = type;
        this.content = content;
        this.fileUrl = fileUrl;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DocumentType getType() {
        return type;
    }

    public void setType(DocumentType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }
}
