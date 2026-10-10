package com.eventease.controller;

import com.eventease.dto.request.AttendanceRequest;
import com.eventease.dto.response.AttendanceResponse;
import com.eventease.entity.Attendance;
import com.eventease.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping
    public ResponseEntity<AttendanceResponse> createAttendance(@Valid @RequestBody AttendanceRequest request) {
        Attendance created = attendanceService.createAttendance(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttendanceResponse> getAttendanceById(@PathVariable Long id) {
        Attendance attendance = attendanceService.getAttendanceById(id);
        return ResponseEntity.ok(toResponse(attendance));
    }

    @GetMapping
    public ResponseEntity<List<AttendanceResponse>> getAllAttendance() {
        List<AttendanceResponse> responses = attendanceService.getAllAttendance()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/registration/{registrationId}")
    public ResponseEntity<AttendanceResponse> getAttendanceByRegistrationId(@PathVariable Long registrationId) {
        Attendance attendance = attendanceService.getAttendanceByRegistrationId(registrationId);
        return ResponseEntity.ok(toResponse(attendance));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AttendanceResponse> updateAttendance(@PathVariable Long id,
                                                               @Valid @RequestBody AttendanceRequest request) {
        Attendance updated = attendanceService.updateAttendance(id, request);
        return ResponseEntity.ok(toResponse(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAttendance(@PathVariable Long id) {
        attendanceService.deleteAttendance(id);
        return ResponseEntity.noContent().build();
    }

    private AttendanceResponse toResponse(Attendance attendance) {
        Long registrationId = attendance.getRegistration() != null ? attendance.getRegistration().getId() : null;
        return new AttendanceResponse(
                attendance.getId(),
                registrationId,
                attendance.getAttended(),
                attendance.getMarkedAt()
        );
    }
}
