package com.devmatch.controller;

import com.devmatch.dto.aireview.candidate.AiReviewCandidateResponse;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateReviewRequest;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.service.AiReviewCandidateAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ai-review/candidates")
@RequiredArgsConstructor
public class AdminAiReviewCandidateController {

    private final AiReviewCandidateAdminService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiReviewCandidateResponse>>> listCandidates() {
        return ResponseEntity.ok(ApiResponse.success(service.listCandidates()));
    }

    @PostMapping("/review")
    public ResponseEntity<ApiResponse<AiReviewCandidateResponse>> reviewCandidate(
            @Valid @RequestBody AiReviewCandidateReviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.reviewCandidate(request)));
    }
}
