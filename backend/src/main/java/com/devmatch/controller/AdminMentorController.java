package com.devmatch.controller;

import com.devmatch.dto.admin.RejectRequest;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.mentor.MentorProfileResponse;
import com.devmatch.entity.MentorStatus;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.MentorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Admin - Mentor", description = "관리자 멘토 심사 API")
@RestController
@RequestMapping("/api/admin/mentor")
@RequiredArgsConstructor
public class AdminMentorController {

    private final MentorService mentorService;

    @Operation(summary = "멘토 신청 목록 조회 (관리자)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<MentorProfileResponse>>> list(
            @RequestParam(value = "status", required = false) MentorStatus status) {
        List<MentorProfileResponse> profiles = mentorService.findAllForAdmin(status);
        return ResponseEntity.ok(ApiResponse.success(profiles));
    }

    @Operation(summary = "멘토 신청 승인")
    @PostMapping("/{profileId}/approve")
    public ResponseEntity<ApiResponse<MentorProfileResponse>> approve(
            @PathVariable Long profileId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        MentorProfileResponse response = mentorService.approve(profileId, userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success("멘토 신청이 승인되었습니다", response));
    }

    @Operation(summary = "멘토 신청 반려")
    @PostMapping("/{profileId}/reject")
    public ResponseEntity<ApiResponse<MentorProfileResponse>> reject(
            @PathVariable Long profileId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RejectRequest request) {
        MentorProfileResponse response = mentorService.reject(
                profileId, userDetails.getUserId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("멘토 신청이 반려되었습니다", response));
    }
}
