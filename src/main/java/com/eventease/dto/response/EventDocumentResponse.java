package com.eventease.dto.response;

import com.eventease.entity.DocumentType;
import java.time.LocalDateTime;

public class EventDocumentResponse {

    private Long id;
    private Long eventId;
    private String title;
    private DocumentType type;
    private String content;
    private String fileUrl;
    private LocalDateTime createdAt;

    public EventDocumentResponse() {
    }

    public EventDocumentResponse(Long id, Long eventId, String title, DocumentType type,
                                 String content, String fileUrl, LocalDateTime createdAt) {
        this.id = id;
        this.eventId = eventId;
        this.title = title;
        this.type = type;
        this.content = content;
        this.fileUrl = fileUrl;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
