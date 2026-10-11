package com.eventease.service;

import com.eventease.dto.response.ImageUploadResponse;
import com.eventease.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinaryService = new CloudinaryService("test-cloud", "test-key", "test-secret");
        cloudinaryService.setRestTemplate(restTemplate);
    }

    @Test
    @DisplayName("isConfigured returns true when credentials present")
    void isConfigured_trueWhenConfigured() {
        assertTrue(cloudinaryService.isConfigured());
    }

    @Test
    @DisplayName("isConfigured returns false when missing credentials")
    void isConfigured_falseWhenEmpty() {
        CloudinaryService unconfigured = new CloudinaryService("", "", "");
        assertFalse(unconfigured.isConfigured());
    }

    @Test
    @DisplayName("uploadImage throws when file is null or empty")
    void uploadImage_nullOrEmptyFile_throwsException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[0]);
        BusinessRuleException ex1 = assertThrows(BusinessRuleException.class, () -> cloudinaryService.uploadImage(null));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatus());

        BusinessRuleException ex2 = assertThrows(BusinessRuleException.class, () -> cloudinaryService.uploadImage(emptyFile));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatus());
    }

    @Test
    @DisplayName("uploadImage throws when file content type is not supported")
    void uploadImage_invalidContentType_throwsException() {
        MockMultipartFile pdfFile = new MockMultipartFile("file", "document.pdf", "application/pdf", new byte[]{1, 2, 3});
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> cloudinaryService.uploadImage(pdfFile));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("Invalid image format"));
    }

    @Test
    @DisplayName("uploadImage throws when file exceeds 5MB")
    void uploadImage_exceedsMaxSize_throwsException() {
        byte[] largeBytes = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile largeFile = new MockMultipartFile("file", "large.png", "image/png", largeBytes);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> cloudinaryService.uploadImage(largeFile));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("5MB"));
    }

    @Test
    @DisplayName("uploadImage throws SERVICE_UNAVAILABLE when Cloudinary is not configured")
    void uploadImage_notConfigured_throwsServiceUnavailable() {
        CloudinaryService unconfigured = new CloudinaryService("", "", "");
        MockMultipartFile file = new MockMultipartFile("file", "poster.jpg", "image/jpeg", new byte[]{1, 2, 3});
        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> unconfigured.uploadImage(file));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertTrue(ex.getMessage().contains("Cloudinary storage is not configured"));
    }

    @Test
    @DisplayName("uploadImage returns ImageUploadResponse on successful upload")
    void uploadImage_success_returnsResponse() {
        MockMultipartFile file = new MockMultipartFile("file", "poster.jpg", "image/jpeg", new byte[]{1, 2, 3});
        Map<String, Object> responseMap = Map.of(
                "secure_url", "https://res.cloudinary.com/test-cloud/image/upload/v123/eventease/events/sample.jpg",
                "public_id", "eventease/events/sample"
        );
        when(restTemplate.postForEntity(eq("https://api.cloudinary.com/v1_1/test-cloud/image/upload"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(responseMap));

        ImageUploadResponse response = cloudinaryService.uploadImage(file);

        assertNotNull(response);
        assertEquals("https://res.cloudinary.com/test-cloud/image/upload/v123/eventease/events/sample.jpg", response.getImageUrl());
        assertEquals("eventease/events/sample", response.getPublicId());
    }

    @Test
    @DisplayName("uploadImage throws BAD_GATEWAY on RestClientResponseException")
    void uploadImage_restException_throwsBadGateway() {
        MockMultipartFile file = new MockMultipartFile("file", "poster.jpg", "image/jpeg", new byte[]{1, 2, 3});
        RestClientResponseException restException = new RestClientResponseException(
                "Cloudinary error", HttpStatus.BAD_REQUEST.value(), "Bad Request", null, "Invalid API key".getBytes(), null
        );
        when(restTemplate.postForEntity(anyString(), any(), eq(Map.class)))
                .thenThrow(restException);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () -> cloudinaryService.uploadImage(file));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
    }

    @Test
    @DisplayName("deleteImage calls Cloudinary destroy endpoint when configured")
    void deleteImage_callsDestroyEndpoint() {
        when(restTemplate.postForEntity(eq("https://api.cloudinary.com/v1_1/test-cloud/image/destroy"), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("result", "ok")));

        assertDoesNotThrow(() -> cloudinaryService.deleteImage("eventease/events/sample"));
        verify(restTemplate, times(1)).postForEntity(eq("https://api.cloudinary.com/v1_1/test-cloud/image/destroy"), any(), eq(Map.class));
    }

    @Test
    @DisplayName("deleteImage does nothing if publicId is null or blank")
    void deleteImage_nullOrBlank_skips() {
        cloudinaryService.deleteImage(null);
        cloudinaryService.deleteImage("   ");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("init parses CLOUDINARY_URL correctly")
    void init_parsesCloudinaryUrl() {
        CloudinaryService service = new CloudinaryService();
        ReflectionTestUtils.setField(service, "cloudinaryUrl", "cloudinary://123456:secretkey@mycloud");
        service.init();

        assertTrue(service.isConfigured());
        assertEquals("mycloud", ReflectionTestUtils.getField(service, "cloudName"));
        assertEquals("123456", ReflectionTestUtils.getField(service, "apiKey"));
        assertEquals("secretkey", ReflectionTestUtils.getField(service, "apiSecret"));
    }
}
