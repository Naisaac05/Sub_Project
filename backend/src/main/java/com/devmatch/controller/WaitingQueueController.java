package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.WaitingQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "WaitingQueue", description = "결제 대기열 API")
@RestController
@RequestMapping("/api/payments/queue")
@RequiredArgsConstructor
public class WaitingQueueController {

    private final WaitingQueueService waitingQueueService;

    @Operation(summary = "대기열 진입", description = "결제 대기열에 등록하고 현재 순번을 반환합니다. 이미 입장 상태면 즉시 통과합니다.")
    @PostMapping("/enter")
    public ResponseEntity<ApiResponse<WaitingQueueService.QueueStatus>> enter(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.success(waitingQueueService.enter(user.getUserId())));
    }

    @Operation(summary = "대기 순번 조회", description = "현재 대기 순번과 전체 대기 인원을 조회합니다. 대기 화면에서 폴링합니다.")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<WaitingQueueService.QueueStatus>> status(
            @AuthenticationPrincipal CustomUserDetails user
    ) {
        return ResponseEntity.ok(ApiResponse.success(waitingQueueService.status(user.getUserId())));
    }
}
