package com.eventease.repository;

import com.eventease.entity.OrganizerStatus;
import com.eventease.entity.Role;
import com.eventease.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    long countByRole(Role role);
    List<User> findByOrganizerStatus(OrganizerStatus organizerStatus);
    List<User> findByOrganizerStatusNot(OrganizerStatus organizerStatus);
    long countByOrganizerStatus(OrganizerStatus organizerStatus);
}
