package com.eventease.controller;

import com.eventease.dto.response.PlatformSummaryResponse;
import com.eventease.dto.response.UserResponse;
import com.eventease.entity.OrganizerStatus;
import com.eventease.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/summary")
    public ResponseEntity<PlatformSummaryResponse> getPlatformSummary() {
        PlatformSummaryResponse summary = adminService.getPlatformSummary();
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/organizer-requests")
    public ResponseEntity<List<UserResponse>> getOrganizerRequests(
            @RequestParam(required = false) OrganizerStatus status
    ) {
        List<UserResponse> requests = adminService.getOrganizerRequests(status);
        return ResponseEntity.ok(requests);
    }

    @PostMapping("/organizer-requests/{userId}/approve")
    public ResponseEntity<UserResponse> approveOrganizerRequest(@PathVariable Long userId) {
        UserResponse response = adminService.approveOrganizerRequest(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/organizer-requests/{userId}/reject")
    public ResponseEntity<UserResponse> rejectOrganizerRequest(@PathVariable Long userId) {
        UserResponse response = adminService.rejectOrganizerRequest(userId);
        return ResponseEntity.ok(response);
    }
}
