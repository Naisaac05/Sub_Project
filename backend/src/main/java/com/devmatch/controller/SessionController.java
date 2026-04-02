package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.session.SessionCreateRequest;
import com.devmatch.dto.session.SessionResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Session", description = "멘토링 세션 API")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "멘토링 세션 생성")
    @PostMapping
    public ResponseEntity<ApiResponse<SessionResponse>> createSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SessionCreateRequest request) {
        SessionResponse response = sessionService.createSession(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("멘토링 세션이 생성되었습니다", response));
    }

    @Operation(summary = "내 세션 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getMySessions(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<SessionResponse> response = sessionService.getMySessions(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "세션 취소")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SessionResponse>> cancelSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        SessionResponse response = sessionService.cancelSession(userDetails.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("세션이 취소되었습니다", response));
    }

    @Operation(summary = "세션 완료 처리")
    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<SessionResponse>> completeSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id) {
        SessionResponse response = sessionService.completeSession(userDetails.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("세션이 완료되었습니다", response));
    }
}
