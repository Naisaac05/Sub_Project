package com.devmatch.controller;

import com.devmatch.dto.admin.dashboard.AdminAuditLogFeedResponse;
import com.devmatch.dto.admin.dashboard.AdminDashboardResponse;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Dashboard", description = "관리자 콘솔 홈 대시보드 API")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService service;

    @Operation(summary = "대시보드 요약 (KPI + 추이 차트 + 처리 큐)")
    @GetMapping
    public ResponseEntity<ApiResponse<AdminDashboardResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success(service.getSummary()));
    }

    @Operation(summary = "최근 감사 로그 피드 (SUPER_ADMIN 만)")
    @GetMapping("/audit-log")
    public ResponseEntity<ApiResponse<AdminAuditLogFeedResponse>> getAuditLog() {
        return ResponseEntity.ok(ApiResponse.success(service.getAuditLogFeed()));
    }
}
