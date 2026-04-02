package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.dto.matching.MatchingAcceptRequest;
import com.devmatch.dto.matching.MatchingRequest;
import com.devmatch.dto.matching.MatchingResponse;
import com.devmatch.dto.matching.MentorRecommendResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.MatchingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Matching", description = "매칭 API")
@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
public class MatchingController {

    private final MatchingService matchingService;

    @Operation(summary = "멘토 추천", description = "테스트 결과 기반 멘토 추천 (?category=Java)")
    @GetMapping("/recommend")
    public ResponseEntity<ApiResponse<List<MentorRecommendResponse>>> recommendMentors(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam String category) {
        List<MentorRecommendResponse> response =
                matchingService.recommendMentors(userDetails.getUserId(), category);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "매칭 신청")
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<MatchingResponse>> requestMatching(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody MatchingRequest request) {
        MatchingResponse response =
                matchingService.requestMatching(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("매칭 신청이 완료되었습니다", response));
    }

    @Operation(summary = "매칭 수락/거절", description = "멘토 전용")
    @PutMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<MatchingResponse>> acceptMatching(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody MatchingAcceptRequest request) {
        MatchingResponse response =
                matchingService.acceptMatching(userDetails.getUserId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "멘티 입장 매칭 내역")
    @GetMapping("/mentee")
    public ResponseEntity<ApiResponse<List<MatchingResponse>>> getMyMatchingsAsMentee(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<MatchingResponse> response =
                matchingService.getMyMatchingsAsMentee(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "멘토 입장 매칭 요청 목록", description = "멘토 전용")
    @GetMapping("/mentor")
    public ResponseEntity<ApiResponse<List<MatchingResponse>>> getMyMatchingsAsMentor(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<MatchingResponse> response =
                matchingService.getMyMatchingsAsMentor(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
