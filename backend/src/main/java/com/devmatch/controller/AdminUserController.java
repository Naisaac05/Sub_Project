package com.devmatch.controller;

import com.devmatch.dto.admin.AdminUserDetailResponse;
import com.devmatch.dto.admin.AdminUserListResponse;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.entity.Role;
import com.devmatch.entity.UserStatus;
import com.devmatch.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - Users", description = "관리자 회원 관리 API")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

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
}
