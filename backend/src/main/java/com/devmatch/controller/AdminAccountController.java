package com.devmatch.controller;

import com.devmatch.dto.admin.AdminCreateRequest;
import com.devmatch.dto.admin.AdminCreateResponse;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.user.UserResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.AdminAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Admin - Admin Accounts", description = "SUPER_ADMIN 전용 관리자 계정 관리")
@RestController
@RequestMapping("/api/admin/admins")
@RequiredArgsConstructor
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    @Operation(summary = "관리자 계정 목록")
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.success(adminAccountService.listAdmins()));
    }

    @Operation(summary = "신규 관리자 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<AdminCreateResponse>> create(
            @Valid @RequestBody AdminCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails superAdmin) {
        var resp = adminAccountService.createAdmin(superAdmin.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("관리자 계정이 생성되었습니다", resp));
    }
}
