package com.eventease.service;

import com.eventease.entity.VolunteerRequirement;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.VolunteerRequirementRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VolunteerRequirementService {

    private final VolunteerRequirementRepository volunteerRequirementRepository;

    public VolunteerRequirementService(VolunteerRequirementRepository volunteerRequirementRepository) {
        this.volunteerRequirementRepository = volunteerRequirementRepository;
    }

    public VolunteerRequirement createRequirement(VolunteerRequirement requirement) {
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
        existing.setRequiredCount(requirement.getRequiredCount());
        existing.setDescription(requirement.getDescription());
        if (requirement.getEvent() != null) {
            existing.setEvent(requirement.getEvent());
        }
        return volunteerRequirementRepository.save(existing);
    }

    public void deleteRequirement(Long id) {
        if (!volunteerRequirementRepository.existsById(id)) {
            throw new ResourceNotFoundException("VolunteerRequirement not found with id: " + id);
        }
        volunteerRequirementRepository.deleteById(id);
    }
}
