package com.devmatch.controller;

import com.devmatch.dto.aireview.candidate.AiReviewCandidateReviewV2Request;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateV2Response;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.service.AiReviewCandidateApprovalV2Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai-review/candidates/v2")
@RequiredArgsConstructor
public class AdminAiReviewCandidateV2Controller {

    private final AiReviewCandidateApprovalV2Service service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AiReviewCandidateV2Response>>> listCandidates() {
        return ResponseEntity.ok(ApiResponse.success(service.listCandidates()));
    }

    @PostMapping("/import-jsonl")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> importJsonlCandidates() {
        return ResponseEntity.ok(ApiResponse.success(Map.of("imported", service.importJsonlCandidates())));
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<ApiResponse<AiReviewCandidateV2Response>> reviewCandidate(
            @PathVariable Long id,
            @Valid @RequestBody AiReviewCandidateReviewV2Request request
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.reviewCandidate(id, request)));
    }
}
