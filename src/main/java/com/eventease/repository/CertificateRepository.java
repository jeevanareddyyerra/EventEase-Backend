package com.eventease.repository;

import com.eventease.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByRegistrationId(Long registrationId);
    boolean existsByRegistrationId(Long registrationId);
}
