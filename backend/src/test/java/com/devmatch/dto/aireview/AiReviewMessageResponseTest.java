package com.devmatch.dto.aireview;

import com.devmatch.entity.AiReviewMessage;
import com.devmatch.entity.AiReviewMessageMode;
import com.devmatch.entity.AiReviewMessageRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewMessageResponseTest {

    @Test
    void from_exposes_ai_quality_metadata() {
        AiReviewMessage message = AiReviewMessage.builder()
                .role(AiReviewMessageRole.AI)
                .mode(AiReviewMessageMode.FREE_ANSWER)
                .content("aria-label은 접근성 이름입니다.")
                .aiRoute("generated_card_fast_path")
                .aiResolvedQuery("aria-label이 무엇인가요?")
                .aiCorrectionType("typo")
                .aiMatchedConceptId("frontend-aria-label")
                .aiAnswerStyle("definition")
                .aiQualityFlags("missing_topic,stale_original_context")
                .aiCandidateId("auto-123")
                .aiLatencyMs(14)
                .build();

        AiReviewMessageResponse response = AiReviewMessageResponse.from(message);

        assertThat(response.getAiRoute()).isEqualTo("generated_card_fast_path");
        assertThat(response.getAiResolvedQuery()).isEqualTo("aria-label이 무엇인가요?");
        assertThat(response.getAiCorrectionType()).isEqualTo("typo");
        assertThat(response.getAiMatchedConceptId()).isEqualTo("frontend-aria-label");
        assertThat(response.getAiAnswerStyle()).isEqualTo("definition");
        assertThat(response.getAiQualityFlags()).containsExactly("missing_topic", "stale_original_context");
        assertThat(response.getAiCandidateId()).isEqualTo("auto-123");
        assertThat(response.getAiLatencyMs()).isEqualTo(14);
    }
}
