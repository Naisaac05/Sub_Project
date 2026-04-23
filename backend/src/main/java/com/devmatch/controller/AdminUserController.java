package com.devmatch.controller;

import com.devmatch.dto.admin.AdminUserDetailResponse;
import com.devmatch.dto.admin.AdminUserListResponse;
import com.devmatch.dto.admin.UserActionRequest;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.entity.Role;
import com.devmatch.entity.UserStatus;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Users", description = "관리자 회원 관리 API")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final com.devmatch.service.MentorSwapService mentorSwapService;

    @Operation(summary = "회원 목록 (페이징)")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<AdminUserListResponse>>> list(
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.list(role, status, q, pageable)));
    }

    @Operation(summary = "회원 상세")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminUserDetailResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminUserService.getDetail(id)));
    }

    @Operation(summary = "회원 비활성화")
    @PostMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable Long id,
            @Valid @RequestBody UserActionRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        adminUserService.deactivate(admin.getUserId(), id, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("회원이 비활성화되었습니다", null));
    }

    @Operation(summary = "회원 재활성화")
    @PostMapping("/{id}/reactivate")
    public ResponseEntity<ApiResponse<Void>> reactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails admin) {
        adminUserService.reactivate(admin.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("회원이 재활성화되었습니다", null));
    }

    @Operation(summary = "회원 영구 삭제")
    @PostMapping("/{id}/delete")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id,
            @Valid @RequestBody UserActionRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        adminUserService.delete(admin.getUserId(), id, request.getReason());
        return ResponseEntity.ok(ApiResponse.success("회원이 삭제되었습니다", null));
    }

    @Operation(summary = "회원 비밀번호 리셋")
    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<com.devmatch.dto.admin.PasswordResetResponse>> resetPassword(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails admin) {
        var resp = adminUserService.resetPassword(admin.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("임시 비밀번호가 발급되었습니다", resp));
    }

    @Operation(summary = "멘티의 멘토 교체")
    @PostMapping("/{menteeId}/swap-mentor")
    public ResponseEntity<ApiResponse<Void>> swapMentor(
            @PathVariable Long menteeId,
            @Valid @RequestBody com.devmatch.dto.admin.MentorSwapRequest request,
            @AuthenticationPrincipal CustomUserDetails admin) {
        mentorSwapService.swap(admin.getUserId(), menteeId, request.getNewMentorId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("멘토가 교체되었습니다", null));
    }
}
