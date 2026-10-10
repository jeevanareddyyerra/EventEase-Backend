package com.eventease.repository;

import com.eventease.entity.VolunteerRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VolunteerRequirementRepository extends JpaRepository<VolunteerRequirement, Long> {
    Optional<VolunteerRequirement> findByEventId(Long eventId);
}
