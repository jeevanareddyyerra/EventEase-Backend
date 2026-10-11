package com.eventease.service;

import com.eventease.dto.response.PlatformSummaryResponse;
import com.eventease.entity.EventStatus;
import com.eventease.entity.Role;
import com.eventease.entity.User;
import com.eventease.repository.EventRepository;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @InjectMocks
    private AdminService adminService;

    @Test
    @DisplayName("Should aggregate platform summary using count queries")
    void shouldAggregatePlatformSummary() {
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByRole(Role.ORGANIZER)).thenReturn(20L);
        when(userRepository.countByRole(Role.USER)).thenReturn(75L);
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(5L);

        when(eventRepository.count()).thenReturn(45L);
        when(eventRepository.countByStatus(EventStatus.PUBLISHED)).thenReturn(30L);
        when(eventRepository.countByStatus(EventStatus.PENDING)).thenReturn(8L);
        when(eventRepository.countByStatus(EventStatus.APPROVED)).thenReturn(4L);
        when(eventRepository.countByStatus(EventStatus.COMPLETED)).thenReturn(2L);
        when(eventRepository.countByStatus(EventStatus.CANCELLED)).thenReturn(1L);

        when(registrationRepository.count()).thenReturn(250L);

        PlatformSummaryResponse summary = adminService.getPlatformSummary();

        assertNotNull(summary);
        assertEquals(100L, summary.getTotalUsers());
        assertEquals(20L, summary.getTotalOrganizers());
        assertEquals(75L, summary.getTotalParticipants());
        assertEquals(5L, summary.getTotalAdmins());

        assertEquals(45L, summary.getTotalEvents());
        assertEquals(30L, summary.getPublishedEvents());
        assertEquals(8L, summary.getPendingEvents());
        assertEquals(4L, summary.getApprovedEvents());
        assertEquals(2L, summary.getCompletedEvents());
        assertEquals(1L, summary.getCancelledEvents());

        assertEquals(250L, summary.getTotalRegistrations());

        verify(userRepository).count();
        verify(userRepository).countByRole(Role.ORGANIZER);
        verify(userRepository).countByRole(Role.USER);
        verify(userRepository).countByRole(Role.ADMIN);

        verify(eventRepository).count();
        verify(eventRepository).countByStatus(EventStatus.PUBLISHED);
        verify(eventRepository).countByStatus(EventStatus.PENDING);
        verify(eventRepository).countByStatus(EventStatus.APPROVED);
        verify(eventRepository).countByStatus(EventStatus.COMPLETED);
        verify(eventRepository).countByStatus(EventStatus.CANCELLED);

        verify(registrationRepository).count();
    }

    @Test
    @DisplayName("Should return organizer requests filtered by status")
    void shouldReturnOrganizerRequestsWithStatus() {
        User user = new User("Jane Organizer", "jane@example.com", "secret", "1234567890", Role.USER);
        user.setId(10L);
        user.setOrganizerStatus(com.eventease.entity.OrganizerStatus.PENDING);

        when(userRepository.findByOrganizerStatus(com.eventease.entity.OrganizerStatus.PENDING))
                .thenReturn(java.util.List.of(user));

        java.util.List<com.eventease.dto.response.UserResponse> requests =
                adminService.getOrganizerRequests(com.eventease.entity.OrganizerStatus.PENDING);

        assertEquals(1, requests.size());
        assertEquals("Jane Organizer", requests.get(0).getName());
        assertEquals(com.eventease.entity.OrganizerStatus.PENDING, requests.get(0).getOrganizerStatus());
    }

    @Test
    @DisplayName("Should approve organizer request: updates role to ORGANIZER and status to APPROVED")
    void shouldApproveOrganizerRequest() {
        User user = new User("Jane Organizer", "jane@example.com", "secret", "1234567890", Role.USER);
        user.setId(10L);
        user.setOrganizerStatus(com.eventease.entity.OrganizerStatus.PENDING);

        when(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.eventease.dto.response.UserResponse response = adminService.approveOrganizerRequest(10L);

        assertEquals(Role.ORGANIZER, response.getRole());
        assertEquals(com.eventease.entity.OrganizerStatus.APPROVED, response.getOrganizerStatus());
        assertEquals(Role.ORGANIZER, user.getRole());
        assertEquals(com.eventease.entity.OrganizerStatus.APPROVED, user.getOrganizerStatus());
    }

    @Test
    @DisplayName("Should reject organizer request: keeps role USER and status REJECTED")
    void shouldRejectOrganizerRequest() {
        User user = new User("Jane Organizer", "jane@example.com", "secret", "1234567890", Role.USER);
        user.setId(10L);
        user.setOrganizerStatus(com.eventease.entity.OrganizerStatus.PENDING);

        when(userRepository.findById(10L)).thenReturn(java.util.Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.eventease.dto.response.UserResponse response = adminService.rejectOrganizerRequest(10L);

        assertEquals(Role.USER, response.getRole());
        assertEquals(com.eventease.entity.OrganizerStatus.REJECTED, response.getOrganizerStatus());
        assertEquals(Role.USER, user.getRole());
        assertEquals(com.eventease.entity.OrganizerStatus.REJECTED, user.getOrganizerStatus());
    }
}
