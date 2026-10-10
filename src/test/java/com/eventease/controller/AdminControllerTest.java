package com.eventease.controller;

import com.eventease.dto.response.PlatformSummaryResponse;
import com.eventease.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController adminController;

    @Test
    @DisplayName("Should return 200 OK with platform summary")
    void shouldReturnPlatformSummary() {
        PlatformSummaryResponse expected = new PlatformSummaryResponse(
                50L, 10L, 38L, 2L,
                20L, 12L, 4L, 2L, 1L, 1L,
                100L
        );
        when(adminService.getPlatformSummary()).thenReturn(expected);

        ResponseEntity<PlatformSummaryResponse> response = adminController.getPlatformSummary();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
        assertEquals(50L, response.getBody().getTotalUsers());
        assertEquals(100L, response.getBody().getTotalRegistrations());

        verify(adminService).getPlatformSummary();
    }
}
