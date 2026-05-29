package com.devmatch.controller;

import com.devmatch.dto.application.ApplicationRequest;
import com.devmatch.dto.application.ApplicationResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping("/submit")
    public ResponseEntity<ApplicationResponse> submitApplication(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestBody ApplicationRequest request) {
        return ResponseEntity.ok(applicationService.submitApplication(user.getUserId(), request));
    }

    @PostMapping("/{id}/confirm-payment")
    public ResponseEntity<ApplicationResponse> confirmPayment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id) {
        return ResponseEntity.ok(applicationService.convertToResponse(applicationService.confirmPayment(user.getUserId(), id)));
    }
}
