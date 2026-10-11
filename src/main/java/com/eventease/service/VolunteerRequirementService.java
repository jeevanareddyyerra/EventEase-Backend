package com.eventease.service;

import com.eventease.entity.Event;
import com.eventease.entity.VolunteerRequirement;
import com.eventease.exception.BusinessRuleException;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.VolunteerRequirementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VolunteerRequirementService {

    private final VolunteerRequirementRepository volunteerRequirementRepository;

    public VolunteerRequirementService(VolunteerRequirementRepository volunteerRequirementRepository) {
        this.volunteerRequirementRepository = volunteerRequirementRepository;
    }

    public VolunteerRequirement createRequirement(VolunteerRequirement requirement) {
        if (requirement != null && requirement.getEvent() != null) {
            checkEventOwnership(requirement.getEvent(), "create");
        }
        return volunteerRequirementRepository.save(requirement);
    }

    public VolunteerRequirement getRequirementById(Long id) {
        return volunteerRequirementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("VolunteerRequirement not found with id: " + id));
    }

    public List<VolunteerRequirement> getAllRequirements() {
        return volunteerRequirementRepository.findAll();
    }

    public VolunteerRequirement updateRequirement(Long id, VolunteerRequirement requirement) {
        VolunteerRequirement existing = getRequirementById(id);
        checkEventOwnership(existing.getEvent(), "update");
        existing.setRequiredCount(requirement.getRequiredCount());
        existing.setDescription(requirement.getDescription());
        if (requirement.getEvent() != null) {
            existing.setEvent(requirement.getEvent());
        }
        return volunteerRequirementRepository.save(existing);
    }

    public void deleteRequirement(Long id) {
        VolunteerRequirement existing = getRequirementById(id);
        checkEventOwnership(existing.getEvent(), "delete");
        volunteerRequirementRepository.delete(existing);
    }

    private void checkEventOwnership(Event event, String action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String && "anonymousUser".equals(auth.getPrincipal()))) {
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isOrganizer = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ORGANIZER"));
            boolean isUserOnly = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")) && !isAdmin && !isOrganizer;

            if (isUserOnly) {
                throw new BusinessRuleException("Users are not authorized to " + action + " volunteer requirements.", HttpStatus.FORBIDDEN);
            }

            if (isOrganizer && !isAdmin) {
                if (event != null && event.getOrganizer() != null && event.getOrganizer().getEmail() != null
                        && !event.getOrganizer().getEmail().equalsIgnoreCase(auth.getName())) {
                    throw new BusinessRuleException("Organizers can only " + action + " volunteer requirements for their own events.", HttpStatus.FORBIDDEN);
                }
            }
        }
    }
}
