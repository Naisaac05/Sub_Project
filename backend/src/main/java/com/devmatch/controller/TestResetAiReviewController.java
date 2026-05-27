// 🧪 테스트 전용: AI 리뷰 세션 초기화 컨트롤러
//
// 환경변수 AI_REVIEW_TEST_RESET_ENABLED=true 일 때만 활성화됨.
// 운영 환경에서는 절대 활성화하지 말 것.
//
// 제거 방법 (테스트 종료 후):
//   1. 이 파일 삭제
//   2. backend/src/test/java/com/devmatch/controller/TestResetAiReviewController*Test.java 삭제
//   3. application.yml 의 app.ai-review.test-reset 블록 4줄 삭제
//   4. AiReviewMessageRepository 의 deleteBySessionId 메서드 (다른 곳에서 안 쓰면) 삭제
//   5. frontend/src/components/ai-review/TestResetButton.tsx 삭제
//   6. frontend/src/lib/ai-review.ts 의 resetAiReviewSession 함수 삭제
//   7. frontend/src/app/tests/results/[id]/review/page.tsx 의 import/render 2줄 삭제
//   8. .env 에서 AI_REVIEW_TEST_RESET_ENABLED, NEXT_PUBLIC_AI_REVIEW_TEST_RESET 제거
// 추가일: 2026-05-27, 추가자: aucu2005@gmail.com
package com.devmatch.controller;

import com.devmatch.dto.common.ApiResponse;
import com.devmatch.entity.AiReviewSession;
import com.devmatch.entity.TestResult;
import com.devmatch.exception.TestNotFoundException;
import com.devmatch.repository.AiReviewMessageRepository;
import com.devmatch.repository.AiReviewSessionRepository;
import com.devmatch.repository.TestResultRepository;
import com.devmatch.security.CustomUserDetails;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.Optional;

@RestController
@RequestMapping("/api/ai-review/test-results")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ai-review.test-reset.enabled", havingValue = "true")
public class TestResetAiReviewController {

    private final AiReviewSessionRepository sessionRepository;
    private final AiReviewMessageRepository messageRepository;
    private final TestResultRepository testResultRepository;

    @PostMapping("/{testResultId}/session/reset")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> resetSession(
            @PathVariable Long testResultId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        Long userId = userDetails.getUserId();

        TestResult result = testResultRepository.findById(testResultId)
                .orElseThrow(() -> new TestNotFoundException("진단평가 결과를 찾을 수 없습니다."));

        if (!Objects.equals(result.getUser().getId(), userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("본인의 진단평가만 초기화할 수 있습니다."));
        }

        Optional<AiReviewSession> existing =
                sessionRepository.findTopByUserIdAndTestResultIdOrderByCreatedAtDesc(userId, testResultId);

        existing.ifPresent(session -> {
            messageRepository.deleteBySessionId(session.getId());
            sessionRepository.delete(session);
        });

        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
