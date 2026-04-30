package com.devmatch.controller;

import com.devmatch.dto.aireview.AiReviewSessionResponse;
import com.devmatch.dto.aireview.AiReviewSubmitRequest;
import com.devmatch.dto.aireview.AiReviewSubmitResponse;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.ai.RuleBasedAiReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai-review")
@RequiredArgsConstructor
public class AiReviewController {

    private final RuleBasedAiReviewService aiReviewService;

    @PostMapping("/test-results/{testResultId}/start")
    public ResponseEntity<ApiResponse<AiReviewSessionResponse>> startReview(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long testResultId
    ) {
        AiReviewSessionResponse response = aiReviewService.startReview(userDetails.getUserId(), testResultId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<AiReviewSessionResponse>> getSession(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId
    ) {
        AiReviewSessionResponse response = aiReviewService.getSession(userDetails.getUserId(), sessionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<ApiResponse<AiReviewSubmitResponse>> submitAnswer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long sessionId,
            @Valid @RequestBody AiReviewSubmitRequest request
    ) {
        AiReviewSubmitResponse response = aiReviewService.submitAnswer(
                userDetails.getUserId(),
                sessionId,
                request.getAnswer(),
                request.getMode()
        );
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
