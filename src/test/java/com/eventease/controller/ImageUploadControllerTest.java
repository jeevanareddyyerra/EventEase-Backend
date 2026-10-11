package com.eventease.controller;

import com.eventease.dto.response.ImageUploadResponse;
import com.eventease.service.CloudinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageUploadControllerTest {

    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private ImageUploadController imageUploadController;

    private MockMultipartFile sampleFile;

    @BeforeEach
    void setUp() {
        sampleFile = new MockMultipartFile("file", "poster.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    @DisplayName("uploadImage delegates to CloudinaryService and returns 200 OK")
    void uploadImage_returnsOk() {
        ImageUploadResponse response = new ImageUploadResponse("https://res.cloudinary.com/test/image/upload/sample.jpg", "sample_id");
        when(cloudinaryService.uploadImage(sampleFile)).thenReturn(response);

        ResponseEntity<ImageUploadResponse> entity = imageUploadController.uploadImage(sampleFile);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertNotNull(entity.getBody());
        assertEquals("https://res.cloudinary.com/test/image/upload/sample.jpg", entity.getBody().getImageUrl());
        assertEquals("sample_id", entity.getBody().getPublicId());
        verify(cloudinaryService).uploadImage(sampleFile);
    }

    @Test
    @DisplayName("deleteImage delegates to CloudinaryService and returns 204 No Content")
    void deleteImage_returnsNoContent() {
        doNothing().when(cloudinaryService).deleteImage("sample_id");

        ResponseEntity<Void> entity = imageUploadController.deleteImage("sample_id");

        assertEquals(HttpStatus.NO_CONTENT, entity.getStatusCode());
        verify(cloudinaryService).deleteImage("sample_id");
    }
}
