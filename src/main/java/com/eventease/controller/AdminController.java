package com.eventease.controller;

import com.eventease.dto.response.PlatformSummaryResponse;
import com.eventease.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
