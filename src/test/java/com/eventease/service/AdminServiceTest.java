package com.eventease.service;

import com.eventease.dto.response.PlatformSummaryResponse;
import com.eventease.entity.EventStatus;
import com.eventease.entity.Role;
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
}
