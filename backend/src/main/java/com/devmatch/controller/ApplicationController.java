package com.devmatch.controller;

import com.devmatch.dto.application.ApplicationRequest;
import com.devmatch.dto.application.ApplicationResponse;
import com.devmatch.service.ApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}
