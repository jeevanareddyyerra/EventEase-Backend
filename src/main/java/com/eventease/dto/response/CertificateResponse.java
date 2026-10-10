package com.eventease.dto.response;

import com.eventease.entity.CertificateStatus;
import java.time.LocalDateTime;

public class CertificateResponse {

    private Long id;
    private Long registrationId;
    private CertificateStatus status;
    private String certificateUrl;
    private LocalDateTime issuedAt;

    public CertificateResponse() {
    }

    public CertificateResponse(Long id, Long registrationId, CertificateStatus status, String certificateUrl, LocalDateTime issuedAt) {
        this.id = id;
        this.registrationId = registrationId;
        this.status = status;
        this.certificateUrl = certificateUrl;
        this.issuedAt = issuedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public CertificateStatus getStatus() {
        return status;
    }

    public void setStatus(CertificateStatus status) {
        this.status = status;
    }

    public String getCertificateUrl() {
        return certificateUrl;
    }

    public void setCertificateUrl(String certificateUrl) {
        this.certificateUrl = certificateUrl;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }
}
