package com.devmatch.controller;

import com.devmatch.config.AiReviewProperties;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateCaptureRequest;
import com.devmatch.dto.aireview.candidate.AiReviewCandidateV2Response;
import com.devmatch.dto.common.ApiResponse;
import com.devmatch.exception.ForbiddenOperationException;
import com.devmatch.service.AiReviewCandidateApprovalV2Service;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/internal/ai-review/candidates")
@RequiredArgsConstructor
public class InternalAiReviewCandidateCaptureController {

    private static final String SERVICE_TOKEN_HEADER = "X-AI-Service-Token";

    private final AiReviewCandidateApprovalV2Service service;
    private final AiReviewProperties aiReviewProperties;

    @PostMapping("/capture")
    public ResponseEntity<ApiResponse<AiReviewCandidateV2Response>> captureCandidate(
            @RequestHeader(value = SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody AiReviewCandidateCaptureRequest request
    ) {
        verifyServiceToken(serviceToken);
        return ResponseEntity.ok(ApiResponse.success(service.captureCandidate(request)));
    }

    // 내부 서비스(파이썬 AI 서버) 전용. 토큰이 설정돼 있으면 상수시간 비교로 검증한다.
    // (파이썬 app/security.py 의 verify_service_token 과 대칭. 운영에서는 prod validator 가 토큰 미설정을 차단)
    private void verifyServiceToken(String presented) {
        String expected = aiReviewProperties.python().serviceToken();
        if (expected == null || expected.isBlank()) {
            return; // 로컬/개발: 토큰 미설정 시 통과
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] presentedBytes = (presented == null ? "" : presented).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, presentedBytes)) {
            throw new ForbiddenOperationException("유효하지 않은 서비스 토큰입니다.");
        }
    }
}
