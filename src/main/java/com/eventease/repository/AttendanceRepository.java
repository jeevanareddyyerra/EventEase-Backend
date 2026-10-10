package com.eventease.repository;

import com.eventease.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByRegistrationId(Long registrationId);
    boolean existsByRegistrationId(Long registrationId);
}
