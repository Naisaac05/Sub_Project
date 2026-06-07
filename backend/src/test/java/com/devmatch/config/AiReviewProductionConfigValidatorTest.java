package com.devmatch.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiReviewProductionConfigValidatorTest {

    @Test
    void run_skipsValidationOutsideProdProfile() {
        AiReviewProductionConfigValidator validator = new AiReviewProductionConfigValidator(
                new MockEnvironment().withProperty("spring.profiles.active", "local"),
                unsafePropertiesWithMissingToken()
        );

        assertThatCode(validator::run).doesNotThrowAnyException();
    }

    @Test
    void run_rejectsUnsafeProductionAiReviewConfig() {
        AiReviewProductionConfigValidator validator = new AiReviewProductionConfigValidator(
                new MockEnvironment().withProperty("spring.profiles.active", "prod"),
                unsafePropertiesWithMissingToken()
        );

        assertThatThrownBy(validator::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.ai-review.python.service-token is required in prod")
                .hasMessageContaining("app.ai-review.stream-timeout-seconds must be > 0 in prod")
                .hasMessageContaining("app.ai-review.candidates-path must not point to JSONL in prod")
                .hasMessageContaining("app.ai-review.auto-candidates-path must not point to JSONL in prod")
                .hasMessageContaining("app.ai-review.limits.max-user-answer-length must be > 0 in prod");
    }

    @Test
    void run_acceptsBoundedProductionAiReviewConfig() {
        AiReviewProductionConfigValidator validator = new AiReviewProductionConfigValidator(
                new MockEnvironment()
                        .withProperty("spring.profiles.active", "production")
                        .withProperty("app.ai-review.stream-timeout-seconds", "45"),
                safeProdProperties()
        );

        assertThatCode(validator::run).doesNotThrowAnyException();
    }

    private static AiReviewProperties unsafePropertiesWithMissingToken() {
        return new AiReviewProperties(
                true,
                AiReviewProperties.Provider.PYTHON,
                null,
                new AiReviewProperties.PythonAi(true, "http://ai:8001", "exaone3.5:2.4b", 0.2, 0, 0, 4, 0, ""),
                new AiReviewProperties.Ollama(true, "exaone3.5:2.4b", "http://ollama:11434", 0.2, 0, 0, 4, 0, 1),
                null,
                new AiReviewProperties.Limits(3, 10, 0),
                null,
                null,
                true,
                0,
                "../ai/app/knowledge/candidates/course_concepts.jsonl",
                "../ai/app/knowledge/candidates/auto_candidates.jsonl"
        );
    }

    private static AiReviewProperties safeProdProperties() {
        return new AiReviewProperties(
                true,
                AiReviewProperties.Provider.PYTHON,
                null,
                new AiReviewProperties.PythonAi(true, "http://ai:8001", "exaone3.5:2.4b", 0.2, 256, 1024, 4, 30, "token"),
                new AiReviewProperties.Ollama(true, "exaone3.5:2.4b", "http://ollama:11434", 0.2, 256, 1024, 4, 30, 1),
                null,
                new AiReviewProperties.Limits(3, 10, 700),
                null,
                null,
                true,
                45,
                "",
                ""
        );
    }
}
