package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.DashboardResponse;
import com.devmatch.dto.lms.EnrollmentResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.LmsDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lms")
@RequiredArgsConstructor
public class LmsDashboardController {

    private final LmsDashboardService dashboardService;

    @GetMapping("/dashboard/{matchingId}")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId
    ) {
        DashboardResponse response = dashboardService.getDashboard(user.getUserId(), matchingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/enrollment/{matchingId}")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> getEnrollment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId
    ) {
        EnrollmentResponse response = dashboardService.getEnrollment(user.getUserId(), matchingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
