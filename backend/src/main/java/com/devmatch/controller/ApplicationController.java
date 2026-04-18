package com.devmatch.controller;

import com.devmatch.dto.application.ApplicationRequest;
import com.devmatch.dto.application.ApplicationResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {
    
    private final ApplicationService applicationService;

    @PostMapping("/submit")
    public ResponseEntity<ApplicationResponse> submitApplication(@RequestBody ApplicationRequest request) {
        return ResponseEntity.ok(applicationService.submitApplication(request));
    }

    @PostMapping("/{id}/confirm-payment")
    public ResponseEntity<ApplicationResponse> confirmPayment(@PathVariable Long id) {
        return ResponseEntity.ok(applicationService.convertToResponse(applicationService.confirmPayment(id)));
    }

    // --- 멘토 전용 매칭 API ---

    @GetMapping("/my-assignments")
    public ResponseEntity<List<ApplicationResponse>> getMyAssignments(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(applicationService.getMyAssignments(user.getUserId()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApplicationResponse> approveApplication(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(applicationService.approveApplication(id, user.getUserId()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApplicationResponse> rejectApplication(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(applicationService.rejectApplication(id, user.getUserId()));
    }
}
