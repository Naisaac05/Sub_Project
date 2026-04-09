package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.*;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.LmsAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "LMS - Assignment", description = "과제/코드리뷰 API")
@RestController @RequestMapping("/api/lms/assignments") @RequiredArgsConstructor
public class LmsAssignmentController {
    private final LmsAssignmentService assignmentService;

    @Operation(summary = "과제 생성") @PostMapping
    public ResponseEntity<ApiResponse<AssignmentResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody AssignmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("과제가 생성되었습니다", assignmentService.create(user.getUserId(), request)));
    }

    @Operation(summary = "과제 목록 조회") @GetMapping
    public ResponseEntity<ApiResponse<List<AssignmentResponse>>> getList(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long matchingId, @RequestParam(required = false) String type) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getList(user.getUserId(), matchingId, type)));
    }

    @Operation(summary = "과제 상세 조회") @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AssignmentResponse>> getDetail(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getDetail(user.getUserId(), id)));
    }

    @Operation(summary = "과제 제출") @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<AssignmentResponse>> submit(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody SubmissionRequest request) {
        return ResponseEntity.ok(ApiResponse.success("과제가 제출되었습니다", assignmentService.submit(user.getUserId(), id, request)));
    }

    @Operation(summary = "과제 피드백") @PostMapping("/{id}/feedback")
    public ResponseEntity<ApiResponse<AssignmentResponse>> feedback(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id,
            @Valid @RequestBody FeedbackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("피드백이 등록되었습니다", assignmentService.feedback(user.getUserId(), id, request)));
    }
}
