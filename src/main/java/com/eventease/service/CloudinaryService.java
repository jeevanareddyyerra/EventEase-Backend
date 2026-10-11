package com.eventease.service;

import com.eventease.dto.response.ImageUploadResponse;
import com.eventease.exception.BusinessRuleException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "image/gif"
    );
    private static final String DEFAULT_FOLDER = "eventease/events";

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Value("${cloudinary.url:}")
    private String cloudinaryUrl;

    private RestTemplate restTemplate = new RestTemplate();

    public CloudinaryService() {
    }

    public CloudinaryService(String cloudName, String apiKey, String apiSecret) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    public void setRestTemplate(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        if (cloudinaryUrl != null && !cloudinaryUrl.isBlank()) {
            try {
                URI uri = URI.create(cloudinaryUrl);
                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] parts = userInfo.split(":", 2);
                    if (this.apiKey == null || this.apiKey.isBlank()) {
                        this.apiKey = parts[0];
                    }
                    if (this.apiSecret == null || this.apiSecret.isBlank()) {
                        this.apiSecret = parts[1];
                    }
                }
                if (uri.getHost() != null && (this.cloudName == null || this.cloudName.isBlank())) {
                    this.cloudName = uri.getHost();
                }
                log.info("Cloudinary configuration populated from CLOUDINARY_URL [cloudName={}]", this.cloudName);
            } catch (Exception e) {
                log.warn("Failed to parse CLOUDINARY_URL: {}", e.getMessage());
            }
        }

        if (isConfigured()) {
            log.info("Cloudinary persistent image storage service is ACTIVE and CONFIGURED [cloudName={}]", cloudName);
        } else {
            log.warn("Cloudinary persistent image storage is NOT configured. Configure CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET in environment variables.");
        }
    }

    public boolean isConfigured() {
        return cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

    public ImageUploadResponse uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Image file is required and must not be empty.", HttpStatus.BAD_REQUEST);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessRuleException(
                    "Invalid image format: " + contentType + ". Allowed formats are JPEG, PNG, WEBP, and GIF.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessRuleException(
                    "Image file size exceeds maximum limit of 5MB.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!isConfigured()) {
            throw new BusinessRuleException(
                    "Cloudinary storage is not configured. Please set CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, and CLOUDINARY_API_SECRET (or CLOUDINARY_URL) in environment variables.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String stringToSign = "folder=" + DEFAULT_FOLDER + "&timestamp=" + timestamp + apiSecret;
        String signature = computeSha1(stringToSign);

        try {
            byte[] fileBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "event_poster.jpg";

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("api_key", apiKey);
            body.add("timestamp", timestamp);
            body.add("folder", DEFAULT_FOLDER);
            body.add("signature", signature);
            body.add("file", new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return originalFilename;
                }
            });

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload";
            ResponseEntity<Map> response = restTemplate.postForEntity(uploadUrl, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> responseBody = response.getBody();
                String secureUrl = (String) responseBody.get("secure_url");
                String publicId = (String) responseBody.get("public_id");

                if (secureUrl == null || secureUrl.isBlank()) {
                    throw new BusinessRuleException("Cloudinary upload failed: missing secure_url in response.", HttpStatus.INTERNAL_SERVER_ERROR);
                }

                log.info("Event poster uploaded successfully to Cloudinary [publicId={}]", publicId);
                return new ImageUploadResponse(secureUrl, publicId);
            } else {
                throw new BusinessRuleException("Cloudinary upload returned unexpected status: " + response.getStatusCode(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (RestClientResponseException e) {
            log.error("Cloudinary HTTP upload error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessRuleException("Failed to upload image to Cloudinary: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        } catch (IOException e) {
            log.error("Failed to read image bytes: {}", e.getMessage());
            throw new BusinessRuleException("Failed to process image file: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public void deleteImage(String publicId) {
        if (publicId == null || publicId.isBlank() || !isConfigured()) {
            return;
        }

        String timestamp = String.valueOf(System.currentTimeMillis() / 1000L);
        String stringToSign = "public_id=" + publicId + "&timestamp=" + timestamp + apiSecret;
        String signature = computeSha1(stringToSign);

        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("public_id", publicId);
            body.add("api_key", apiKey);
            body.add("timestamp", timestamp);
            body.add("signature", signature);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            String destroyUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/image/destroy";
            restTemplate.postForEntity(destroyUrl, requestEntity, Map.class);
            log.info("Obsolete event poster deleted from Cloudinary [publicId={}]", publicId);
        } catch (Exception e) {
            log.warn("Non-fatal: Failed to delete obsolete event poster from Cloudinary [publicId={}]: {}", publicId, e.getMessage());
        }
    }

    private String computeSha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 algorithm not available", e);
        }
    }
}
