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

    @Test
    @DisplayName("Should return organizer requests")
    void shouldReturnOrganizerRequests() {
        com.eventease.dto.response.UserResponse req = new com.eventease.dto.response.UserResponse(
                1L, "Jane", "jane@example.com", "12345", com.eventease.entity.Role.USER,
                com.eventease.entity.OrganizerStatus.PENDING, java.time.LocalDateTime.now()
        );
        when(adminService.getOrganizerRequests(com.eventease.entity.OrganizerStatus.PENDING))
                .thenReturn(java.util.List.of(req));

        ResponseEntity<java.util.List<com.eventease.dto.response.UserResponse>> response =
                adminController.getOrganizerRequests(com.eventease.entity.OrganizerStatus.PENDING);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(com.eventease.entity.OrganizerStatus.PENDING, response.getBody().get(0).getOrganizerStatus());
    }

    @Test
    @DisplayName("Should approve organizer request")
    void shouldApproveOrganizerRequest() {
        com.eventease.dto.response.UserResponse approved = new com.eventease.dto.response.UserResponse(
                1L, "Jane", "jane@example.com", "12345", com.eventease.entity.Role.ORGANIZER,
                com.eventease.entity.OrganizerStatus.APPROVED, java.time.LocalDateTime.now()
        );
        when(adminService.approveOrganizerRequest(1L)).thenReturn(approved);

        ResponseEntity<com.eventease.dto.response.UserResponse> response =
                adminController.approveOrganizerRequest(1L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(com.eventease.entity.Role.ORGANIZER, response.getBody().getRole());
        assertEquals(com.eventease.entity.OrganizerStatus.APPROVED, response.getBody().getOrganizerStatus());
    }

    @Test
    @DisplayName("Should reject organizer request")
    void shouldRejectOrganizerRequest() {
        com.eventease.dto.response.UserResponse rejected = new com.eventease.dto.response.UserResponse(
                1L, "Jane", "jane@example.com", "12345", com.eventease.entity.Role.USER,
                com.eventease.entity.OrganizerStatus.REJECTED, java.time.LocalDateTime.now()
        );
        when(adminService.rejectOrganizerRequest(1L)).thenReturn(rejected);

        ResponseEntity<com.eventease.dto.response.UserResponse> response =
                adminController.rejectOrganizerRequest(1L);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(com.eventease.entity.OrganizerStatus.REJECTED, response.getBody().getOrganizerStatus());
    }
}
