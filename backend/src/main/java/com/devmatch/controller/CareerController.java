package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.lms.*;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.CareerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "LMS - Career", description = "취업 지원 API")
@RestController
@RequestMapping("/api/lms")
@RequiredArgsConstructor
public class CareerController {

    private final CareerService careerService;

    @Operation(summary = "이력서 업로드")
    @PostMapping("/resumes")
    public ResponseEntity<ApiResponse<ResumeResponse>> uploadResume(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long matchingId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("이력서가 업로드되었습니다",
                        careerService.uploadResume(user.getUserId(), matchingId, file)));
    }

    @Operation(summary = "이력서 목록 조회")
    @GetMapping("/resumes")
    public ResponseEntity<ApiResponse<List<ResumeResponse>>> getResumes(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long matchingId) {
        return ResponseEntity.ok(ApiResponse.success(
                careerService.getResumes(user.getUserId(), matchingId)));
    }

    @Operation(summary = "이력서 피드백")
    @PostMapping("/resumes/{id}/feedback")
    public ResponseEntity<ApiResponse<ResumeResponse>> feedbackResume(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id,
            @Valid @RequestBody ResumeFeedbackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("피드백이 등록되었습니다",
                careerService.feedbackResume(user.getUserId(), id, request)));
    }

    @Operation(summary = "모의 면접 기록")
    @PostMapping("/mock-interviews")
    public ResponseEntity<ApiResponse<MockInterviewResponse>> createMockInterview(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody MockInterviewCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("모의 면접이 기록되었습니다",
                        careerService.createMockInterview(user.getUserId(), request)));
    }

    @Operation(summary = "모의 면접 목록 조회")
    @GetMapping("/mock-interviews")
    public ResponseEntity<ApiResponse<List<MockInterviewResponse>>> getMockInterviews(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam Long matchingId) {
        return ResponseEntity.ok(ApiResponse.success(
                careerService.getMockInterviews(user.getUserId(), matchingId)));
    }
}
