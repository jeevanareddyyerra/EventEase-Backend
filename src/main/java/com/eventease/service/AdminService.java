package com.eventease.service;

import com.eventease.dto.response.PlatformSummaryResponse;
import com.eventease.entity.EventStatus;
import com.eventease.entity.Role;
import com.eventease.repository.EventRepository;
import com.eventease.repository.RegistrationRepository;
import com.eventease.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        return new PlatformSummaryResponse(
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
    }
}
