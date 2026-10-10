package com.eventease.dto.request;

import com.eventease.entity.CertificateStatus;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class CertificateRequest {

    @NotNull(message = "Registration ID is required")
    private Long registrationId;

    private CertificateStatus status;

    private String certificateUrl;

    private LocalDateTime issuedAt;

    public CertificateRequest() {
    }

    public CertificateRequest(Long registrationId) {
        this.registrationId = registrationId;
    }

    public CertificateRequest(Long registrationId, String certificateUrl) {
        this.registrationId = registrationId;
        this.certificateUrl = certificateUrl;
    }

    public CertificateRequest(Long registrationId, CertificateStatus status, String certificateUrl, LocalDateTime issuedAt) {
        this.registrationId = registrationId;
        this.status = status;
        this.certificateUrl = certificateUrl;
        this.issuedAt = issuedAt;
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
