package com.eventease.controller;

import com.eventease.dto.request.CertificateRequest;
import com.eventease.dto.response.CertificateResponse;
import com.eventease.entity.Certificate;
import com.eventease.service.CertificateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping
    public ResponseEntity<CertificateResponse> createCertificate(@Valid @RequestBody CertificateRequest request) {
        Certificate created = certificateService.createCertificate(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CertificateResponse> getCertificateById(@PathVariable Long id) {
        Certificate certificate = certificateService.getCertificateById(id);
        return ResponseEntity.ok(toResponse(certificate));
    }

    @GetMapping
    public ResponseEntity<List<CertificateResponse>> getAllCertificates() {
        List<CertificateResponse> responses = certificateService.getAllCertificates()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/registration/{registrationId}")
    public ResponseEntity<CertificateResponse> getCertificateByRegistrationId(@PathVariable Long registrationId) {
        Certificate certificate = certificateService.getCertificateByRegistrationId(registrationId);
        return ResponseEntity.ok(toResponse(certificate));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CertificateResponse> updateCertificate(@PathVariable Long id,
                                                                 @Valid @RequestBody CertificateRequest request) {
        Certificate updated = certificateService.updateCertificate(id, request);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCertificate(@PathVariable Long id) {
        certificateService.deleteCertificate(id);
        return ResponseEntity.noContent().build();
    }

    private CertificateResponse toResponse(Certificate certificate) {
        Long registrationId = certificate.getRegistration() != null ? certificate.getRegistration().getId() : null;
        return new CertificateResponse(
                certificate.getId(),
                registrationId,
                certificate.getStatus(),
                certificate.getCertificateUrl(),
                certificate.getIssuedAt()
        );
    }
}
