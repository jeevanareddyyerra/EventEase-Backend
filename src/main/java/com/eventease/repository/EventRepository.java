package com.eventease.repository;

import com.eventease.entity.Event;
import com.eventease.entity.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
    List<Event> findByStatus(EventStatus status);
    List<Event> findByOrganizerId(Long organizerId);
    long countByStatus(EventStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT e FROM Event e LEFT JOIN FETCH e.volunteerRequirement LEFT JOIN FETCH e.organizer WHERE e.id = :id")
    java.util.Optional<Event> findByIdWithDetails(@org.springframework.data.repository.query.Param("id") Long id);
}
