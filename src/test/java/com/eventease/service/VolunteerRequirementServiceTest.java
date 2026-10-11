package com.eventease.service;

import com.eventease.entity.Event;
import com.eventease.entity.Role;
import com.eventease.entity.User;
import com.eventease.entity.VolunteerRequirement;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.VolunteerRequirementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VolunteerRequirementServiceTest {

    @Mock
    private VolunteerRequirementRepository volunteerRequirementRepository;

    @InjectMocks
    private VolunteerRequirementService volunteerRequirementService;

    private User organizerUser;
    private User otherOrganizer;
    private Event sampleEvent;
    private VolunteerRequirement sampleRequirement;

    @BeforeEach
    void setUp() {
        organizerUser = new User();
        organizerUser.setId(10L);
        organizerUser.setEmail("organizer@eventease.com");
        organizerUser.setRole(Role.ORGANIZER);

        otherOrganizer = new User();
        otherOrganizer.setId(20L);
        otherOrganizer.setEmail("other@eventease.com");
        otherOrganizer.setRole(Role.ORGANIZER);

        sampleEvent = new Event();
        sampleEvent.setId(100L);
        sampleEvent.setTitle("Tech Fest 2026");
        sampleEvent.setOrganizer(organizerUser);

        sampleRequirement = new VolunteerRequirement();
        sampleRequirement.setId(1L);
        sampleRequirement.setEvent(sampleEvent);
        sampleRequirement.setRequiredCount(5);
        sampleRequirement.setDescription("Assist with venue check-in");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String email, String role) {
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(
                email,
                "password",
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
        );
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    @Test
    @DisplayName("createRequirement: Organizer can create volunteer requirement for their own event")
    void testCreateRequirement_OwnEvent_Success() {
        authenticateAs("organizer@eventease.com", "ORGANIZER");
        when(volunteerRequirementRepository.save(any(VolunteerRequirement.class))).thenAnswer(i -> i.getArgument(0));

        VolunteerRequirement saved = volunteerRequirementService.createRequirement(sampleRequirement);

        assertNotNull(saved);
        assertEquals(5, saved.getRequiredCount());
        verify(volunteerRequirementRepository).save(sampleRequirement);
    }

    @Test
    @DisplayName("createRequirement: Organizer cannot create requirement for another organizer's event")
    void testCreateRequirement_OtherEvent_Forbidden() {
        authenticateAs("other@eventease.com", "ORGANIZER");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                volunteerRequirementService.createRequirement(sampleRequirement)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only create volunteer requirements for their own events"));
        verify(volunteerRequirementRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRequirement: Normal USER cannot create volunteer requirements")
    void testCreateRequirement_UserRole_Forbidden() {
        authenticateAs("participant@eventease.com", "USER");

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                volunteerRequirementService.createRequirement(sampleRequirement)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Users are not authorized to create volunteer requirements"));
        verify(volunteerRequirementRepository, never()).save(any());
    }

    @Test
    @DisplayName("createRequirement: Admin can create volunteer requirement for any event")
    void testCreateRequirement_Admin_Success() {
        authenticateAs("admin@eventease.com", "ADMIN");
        when(volunteerRequirementRepository.save(any(VolunteerRequirement.class))).thenAnswer(i -> i.getArgument(0));

        VolunteerRequirement saved = volunteerRequirementService.createRequirement(sampleRequirement);

        assertNotNull(saved);
        verify(volunteerRequirementRepository).save(sampleRequirement);
    }

    @Test
    @DisplayName("updateRequirement: Organizer can update requirement for their own event")
    void testUpdateRequirement_OwnEvent_Success() {
        authenticateAs("organizer@eventease.com", "ORGANIZER");
        when(volunteerRequirementRepository.findById(1L)).thenReturn(Optional.of(sampleRequirement));
        when(volunteerRequirementRepository.save(any(VolunteerRequirement.class))).thenAnswer(i -> i.getArgument(0));

        VolunteerRequirement updatedData = new VolunteerRequirement();
        updatedData.setRequiredCount(10);
        updatedData.setDescription("Updated duties");

        VolunteerRequirement result = volunteerRequirementService.updateRequirement(1L, updatedData);

        assertEquals(10, result.getRequiredCount());
        assertEquals("Updated duties", result.getDescription());
        verify(volunteerRequirementRepository).save(sampleRequirement);
    }

    @Test
    @DisplayName("updateRequirement: Organizer cannot update requirement for another organizer's event")
    void testUpdateRequirement_OtherEvent_Forbidden() {
        authenticateAs("other@eventease.com", "ORGANIZER");
        when(volunteerRequirementRepository.findById(1L)).thenReturn(Optional.of(sampleRequirement));

        VolunteerRequirement updatedData = new VolunteerRequirement();
        updatedData.setRequiredCount(10);

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                volunteerRequirementService.updateRequirement(1L, updatedData)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only update volunteer requirements for their own events"));
    }

    @Test
    @DisplayName("deleteRequirement: Organizer can delete requirement for their own event")
    void testDeleteRequirement_OwnEvent_Success() {
        authenticateAs("organizer@eventease.com", "ORGANIZER");
        when(volunteerRequirementRepository.findById(1L)).thenReturn(Optional.of(sampleRequirement));

        volunteerRequirementService.deleteRequirement(1L);

        verify(volunteerRequirementRepository).delete(sampleRequirement);
    }

    @Test
    @DisplayName("deleteRequirement: Organizer cannot delete requirement for another organizer's event")
    void testDeleteRequirement_OtherEvent_Forbidden() {
        authenticateAs("other@eventease.com", "ORGANIZER");
        when(volunteerRequirementRepository.findById(1L)).thenReturn(Optional.of(sampleRequirement));

        BusinessRuleException ex = assertThrows(BusinessRuleException.class, () ->
                volunteerRequirementService.deleteRequirement(1L)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Organizers can only delete volunteer requirements for their own events"));
        verify(volunteerRequirementRepository, never()).delete(any());
    }

    @Test
    @DisplayName("getRequirementById: returns requirement or throws ResourceNotFoundException")
    void testGetRequirementById() {
        when(volunteerRequirementRepository.findById(1L)).thenReturn(Optional.of(sampleRequirement));
        when(volunteerRequirementRepository.findById(99L)).thenReturn(Optional.empty());

        assertEquals(sampleRequirement, volunteerRequirementService.getRequirementById(1L));
        assertThrows(ResourceNotFoundException.class, () -> volunteerRequirementService.getRequirementById(99L));
    }
}
