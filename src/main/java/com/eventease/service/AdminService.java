package com.eventease.service;

import com.eventease.dto.response.PlatformSummaryResponse;
import com.eventease.dto.response.UserResponse;
import com.eventease.entity.EventStatus;
import com.eventease.entity.OrganizerStatus;
import com.eventease.entity.Role;
import com.eventease.entity.User;
import com.eventease.exception.ResourceNotFoundException;
import com.eventease.repository.EventRepository;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;

    public AdminService(
            UserRepository userRepository,
            EventRepository eventRepository,
            RegistrationRepository registrationRepository
    ) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional(readOnly = true)
    public PlatformSummaryResponse getPlatformSummary() {
        long totalUsers = userRepository.count();
        long totalOrganizers = userRepository.countByRole(Role.ORGANIZER);
        long totalParticipants = userRepository.countByRole(Role.USER);
        long totalAdmins = userRepository.countByRole(Role.ADMIN);

        long totalEvents = eventRepository.count();
        long publishedEvents = eventRepository.countByStatus(EventStatus.PUBLISHED);
        long pendingEvents = eventRepository.countByStatus(EventStatus.PENDING);
        long approvedEvents = eventRepository.countByStatus(EventStatus.APPROVED);
        long completedEvents = eventRepository.countByStatus(EventStatus.COMPLETED);
        long cancelledEvents = eventRepository.countByStatus(EventStatus.CANCELLED);

        long totalRegistrations = registrationRepository.count();

        PlatformSummaryResponse summary = new PlatformSummaryResponse(
                totalUsers,
                totalOrganizers,
                totalParticipants,
                totalAdmins,
                totalEvents,
                publishedEvents,
                pendingEvents,
                approvedEvents,
                completedEvents,
                cancelledEvents,
                totalRegistrations
        );
        summary.setPendingOrganizers(userRepository.countByOrganizerStatus(OrganizerStatus.PENDING));
        return summary;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getOrganizerRequests(OrganizerStatus status) {
        List<User> users;
        if (status != null) {
            users = userRepository.findByOrganizerStatus(status);
        } else {
            users = userRepository.findByOrganizerStatusNot(OrganizerStatus.NONE);
        }
        return users.stream()
                .map(this::toUserResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse approveOrganizerRequest(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setRole(Role.ORGANIZER);
        user.setOrganizerStatus(OrganizerStatus.APPROVED);
        User saved = userRepository.save(user);
        return toUserResponse(saved);
    }

    @Transactional
    public UserResponse rejectOrganizerRequest(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setRole(Role.USER);
        user.setOrganizerStatus(OrganizerStatus.REJECTED);
        User saved = userRepository.save(user);
        return toUserResponse(saved);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getOrganizerStatus(),
                user.getCreatedAt()
        );
    }
}
