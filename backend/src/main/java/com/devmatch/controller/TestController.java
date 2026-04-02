package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.test.TestDetailResponse;
import com.devmatch.dto.test.TestListResponse;
import com.devmatch.dto.test.TestResultResponse;
import com.devmatch.dto.test.TestSubmitRequest;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.TestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Test", description = "테스트 API")
@RestController
@RequestMapping("/api/tests")
@RequiredArgsConstructor
public class TestController {

    private final TestService testService;

    @Operation(summary = "테스트 목록 조회", description = "분야별 필터링 가능 (?category=Java)")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TestListResponse>>> getTests(
            @RequestParam(required = false) String category) {
        List<TestListResponse> response = testService.getTests(category);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "테스트 상세 조회", description = "문제 목록 포함 (정답 미포함)")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TestDetailResponse>> getTestDetail(@PathVariable Long id) {
        TestDetailResponse response = testService.getTestDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "답안 제출 + 자동 채점")
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<TestResultResponse>> submitTest(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody TestSubmitRequest request) {
        TestResultResponse response = testService.submitTest(userDetails.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success("채점이 완료되었습니다", response));
    }

    @Operation(summary = "내 테스트 결과 목록")
    @GetMapping("/results")
    public ResponseEntity<ApiResponse<List<TestResultResponse>>> getMyResults(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<TestResultResponse> response = testService.getMyResults(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
