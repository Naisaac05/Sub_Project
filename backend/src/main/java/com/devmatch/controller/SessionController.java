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

@Tag(name = "Session", description = "멘토링 세션 API (Google Calendar/Meet 연동)")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @Operation(summary = "멘토링 세션 생성", description = "매칭 ACCEPTED 후 세션을 생성합니다. Google Calendar 이벤트 + Meet 링크가 자동 생성됩니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<SessionResponse>> createSession(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody SessionCreateRequest request
    ) {
        SessionResponse response = sessionService.createSession(user.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("멘토링 세션이 생성되었습니다", response));
    }

    @Operation(summary = "내 세션 목록 조회", description = "멘티/멘토 모두 자신의 세션 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionResponse>>> getMySessions(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        List<SessionResponse> sessions = sessionService.getMySessions(user.getUserId());
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @Operation(summary = "세션 취소", description = "예정된 세션을 취소합니다. Google Calendar 이벤트도 함께 삭제됩니다.")
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<SessionResponse>> cancelSession(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id
    ) {
        SessionResponse response = sessionService.cancelSession(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("세션이 취소되었습니다", response));
    }

    @Operation(summary = "세션 완료 처리", description = "멘토가 세션을 완료 처리합니다.")
    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<SessionResponse>> completeSession(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id
    ) {
        SessionResponse response = sessionService.completeSession(user.getUserId(), id);
        return ResponseEntity.ok(ApiResponse.success("세션이 완료 처리되었습니다", response));
    }
}
