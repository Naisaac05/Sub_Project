package com.devmatch.controller;

import com.devmatch.dto.aireview.candidate.AiReviewCandidateCaptureRequest;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateV2Response;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.service.AiReviewCandidateApprovalV2Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/ai-review/candidates")
@RequiredArgsConstructor
public class InternalAiReviewCandidateCaptureController {

    private final AiReviewCandidateApprovalV2Service service;

    @PostMapping("/capture")
    public ResponseEntity<ApiResponse<AiReviewCandidateV2Response>> captureCandidate(
            @Valid @RequestBody AiReviewCandidateCaptureRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.captureCandidate(request)));
    }
}
