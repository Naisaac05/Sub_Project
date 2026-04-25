package com.devmatch.controller;

import com.devmatch.dto.admin.menteechange.*;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.entity.MentorChangeRequestStatus;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AdminMentorChangeRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Mentor Change", description = "관리자 멘토 교체 신청 심사 API")
@RestController
@RequestMapping("/api/admin/mentor-change-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminMentorChangeRequestController {

    private final AdminMentorChangeRequestService service;

    @Operation(summary = "신청 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminMentorChangeListItemResponse>>> list(
            @RequestParam(required = false) MentorChangeRequestStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.list(status, pageable)));
    }

    @Operation(summary = "신청 상세")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminMentorChangeDetailResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(service.get(id)));
    }

    @Operation(summary = "후보 멘토 조회 (현재 멘토 제외, sameCategoryOnly=true 면 같은 카테고리만)")
    @GetMapping("/{id}/candidate-mentors")
    public ResponseEntity<ApiResponse<Page<CandidateMentorResponse>>> candidates(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "true") boolean sameCategoryOnly,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                service.listCandidateMentors(id, keyword, sameCategoryOnly, pageable)));
    }

    @Operation(summary = "승인 (멘토 교체 실행)")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AdminMentorChangeDetailResponse>> approve(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable Long id,
            @Valid @RequestBody AdminMentorChangeApproveRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "멘토 교체가 완료되었습니다",
                service.approve(admin.getUserId(), id, request)));
    }

    @Operation(summary = "반려")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AdminMentorChangeDetailResponse>> reject(
            @AuthenticationPrincipal CustomUserDetails admin,
            @PathVariable Long id,
            @Valid @RequestBody AdminMentorChangeRejectRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "신청이 반려되었습니다",
                service.reject(admin.getUserId(), id, request)));
    }
}
