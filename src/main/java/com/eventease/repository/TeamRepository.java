package com.eventease.repository;

import com.eventease.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByEventId(Long eventId);
    boolean existsByEventIdAndName(Long eventId, String name);
    boolean existsByEventIdAndLeaderId(Long eventId, Long leaderId);
}
