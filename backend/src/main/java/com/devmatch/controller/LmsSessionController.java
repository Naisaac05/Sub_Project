package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.*;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.LmsSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "LMS Session", description = "LMS 캘린더 기반 세션 관리 API")
@RestController
@RequestMapping("/api/lms/sessions/{matchingId}")
@RequiredArgsConstructor
public class LmsSessionController {

    private final LmsSessionService lmsSessionService;

    // ─── Sessions ───

    @Operation(summary = "세션 목록 조회")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SessionListResponse>>> getSessions(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId) {
        return ResponseEntity.ok(ApiResponse.success(
                lmsSessionService.getSessions(user.getUserId(), matchingId)));
    }

    @Operation(summary = "세션 완료 처리")
    @PutMapping("/{sessionId}/complete")
    public ResponseEntity<ApiResponse<SessionListResponse>> completeSession(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success("세션이 완료 처리되었습니다",
                lmsSessionService.completeSession(user.getUserId(), matchingId, sessionId)));
    }

    @Operation(summary = "세션 취소")
    @PutMapping("/{sessionId}/cancel")
    public ResponseEntity<ApiResponse<SessionListResponse>> cancelSession(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success("세션이 취소되었습니다",
                lmsSessionService.cancelSession(user.getUserId(), matchingId, sessionId)));
    }

    // ─── Time Slots ───

    @Operation(summary = "월별 가용시간 슬롯 조회")
    @GetMapping("/slots")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getSlots(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @RequestParam String month) {
        return ResponseEntity.ok(ApiResponse.success(
                lmsSessionService.getSlots(user.getUserId(), matchingId, month)));
    }

    @Operation(summary = "특정 날짜 예약 가능 슬롯 조회")
    @GetMapping("/slots/available")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getAvailableSlots(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @RequestParam LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(
                lmsSessionService.getAvailableSlots(user.getUserId(), matchingId, date)));
    }

    @Operation(summary = "가용시간 슬롯 등록 (멘토)")
    @PostMapping("/slots")
    public ResponseEntity<ApiResponse<TimeSlotResponse>> createSlot(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @Valid @RequestBody TimeSlotCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "가용시간이 등록되었습니다",
                lmsSessionService.createSlot(user.getUserId(), matchingId, request)));
    }

    @Operation(summary = "가용시간 슬롯 삭제 (멘토)")
    @DeleteMapping("/slots/{slotId}")
    public ResponseEntity<ApiResponse<Void>> deleteSlot(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @PathVariable Long slotId) {
        lmsSessionService.deleteSlot(user.getUserId(), matchingId, slotId);
        return ResponseEntity.ok(ApiResponse.success("가용시간이 삭제되었습니다", null));
    }

    // ─── Booking ───

    @Operation(summary = "세션 예약 (멘티가 슬롯 선택)")
    @PostMapping("/book")
    public ResponseEntity<ApiResponse<SessionListResponse>> bookSession(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @Valid @RequestBody BookSessionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "세션이 예약되었습니다",
                lmsSessionService.bookSession(user.getUserId(), matchingId, request)));
    }

    // ─── Change Requests ───

    @Operation(summary = "변경 요청 목록 조회")
    @GetMapping("/change-requests")
    public ResponseEntity<ApiResponse<List<ChangeRequestResponse>>> getChangeRequests(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @RequestParam Long sessionId) {
        return ResponseEntity.ok(ApiResponse.success(
                lmsSessionService.getChangeRequests(user.getUserId(), matchingId, sessionId)));
    }

    @Operation(summary = "세션 변경 요청 생성")
    @PostMapping("/change-request")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> createChangeRequest(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @Valid @RequestBody ChangeRequestCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "변경 요청이 생성되었습니다",
                lmsSessionService.createChangeRequest(user.getUserId(), matchingId, request)));
    }

    @Operation(summary = "변경 요청 승인")
    @PutMapping("/change-request/{requestId}/approve")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> approveChangeRequest(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success("변경 요청이 승인되었습니다",
                lmsSessionService.approveChangeRequest(user.getUserId(), matchingId, requestId)));
    }

    @Operation(summary = "변경 요청 거절")
    @PutMapping("/change-request/{requestId}/reject")
    public ResponseEntity<ApiResponse<ChangeRequestResponse>> rejectChangeRequest(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long matchingId,
            @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.success("변경 요청이 거절되었습니다",
                lmsSessionService.rejectChangeRequest(user.getUserId(), matchingId, requestId)));
    }
}
