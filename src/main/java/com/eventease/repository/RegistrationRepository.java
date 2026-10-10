package com.eventease.repository;

import com.eventease.entity.Registration;
import com.eventease.entity.RegistrationStatus;
import com.eventease.entity.RegistrationType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    @EntityGraph(attributePaths = {"user", "event"})
    List<Registration> findByEventId(Long eventId);

    @EntityGraph(attributePaths = {"user", "event"})
    List<Registration> findByEventIdAndType(Long eventId, RegistrationType type);

    @EntityGraph(attributePaths = {"user", "event"})
    List<Registration> findByEventIdAndStatus(Long eventId, RegistrationStatus status);

    @EntityGraph(attributePaths = {"user", "event"})
    List<Registration> findByEventIdAndTypeAndStatus(Long eventId, RegistrationType type, RegistrationStatus status);

    @EntityGraph(attributePaths = {"user", "event"})
    List<Registration> findByUserId(Long userId);

    long countByEventId(Long eventId);

    long countByEventIdAndType(Long eventId, RegistrationType type);

    long countByEventIdAndStatus(Long eventId, RegistrationStatus status);

    boolean existsByUserIdAndEventId(Long userId, Long eventId);

    boolean existsByUserIdAndEventIdAndTypeAndStatusIn(Long userId, Long eventId, RegistrationType type, Collection<RegistrationStatus> statuses);

    long countByEventIdAndTypeAndStatusIn(Long eventId, RegistrationType type, Collection<RegistrationStatus> statuses);

    long countByEventIdAndTypeAndStatus(Long eventId, RegistrationType type, RegistrationStatus status);
}
