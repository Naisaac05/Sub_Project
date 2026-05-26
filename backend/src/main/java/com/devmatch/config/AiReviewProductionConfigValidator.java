package com.devmatch.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Component
public class AiReviewProductionConfigValidator implements ApplicationRunner {

    private final Environment environment;
    private final AiReviewProperties properties;

    public AiReviewProductionConfigValidator(Environment environment, AiReviewProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        run();
    }

    void run() {
        if (!isProductionProfile()) {
            return;
        }

        List<String> errors = new ArrayList<>();
        requireText(errors, properties.python().serviceToken(), "app.ai-review.python.service-token is required in prod");
        requireConfiguredPositive(
                errors,
                "app.ai-review.stream-timeout-seconds",
                properties.streamTimeoutSeconds(),
                "app.ai-review.stream-timeout-seconds must be > 0 in prod"
        );
        requirePositive(errors, properties.python().readTimeoutSeconds(), "app.ai-review.python.read-timeout-seconds must be > 0 in prod");
        requirePositive(errors, properties.ollama().readTimeoutSeconds(), "app.ai-review.ollama.read-timeout-seconds must be > 0 in prod");
        requirePositive(errors, properties.python().maxTokens(), "app.ai-review.python.max-tokens must be > 0 in prod");
        requirePositive(errors, properties.python().numCtx(), "app.ai-review.python.num-ctx must be > 0 in prod");
        requirePositive(errors, properties.ollama().maxTokens(), "app.ai-review.ollama.max-tokens must be > 0 in prod");
        requirePositive(errors, properties.ollama().numCtx(), "app.ai-review.ollama.num-ctx must be > 0 in prod");
        requirePositive(errors, properties.limits().maxUserAnswerLength(), "app.ai-review.limits.max-user-answer-length must be > 0 in prod");
        rejectJsonl(errors, properties.candidatesPath(), "app.ai-review.candidates-path must not point to JSONL in prod");
        rejectJsonl(errors, properties.autoCandidatesPath(), "app.ai-review.auto-candidates-path must not point to JSONL in prod");

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Unsafe AI Review production configuration: " + String.join("; ", errors));
        }
    }

    private boolean isProductionProfile() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals("prod") || value.equals("production"));
    }

    private static void requireText(List<String> errors, String value, String message) {
        if (value == null || value.isBlank()) {
            errors.add(message);
        }
    }

    private static void requirePositive(List<String> errors, int value, String message) {
        if (value <= 0) {
            errors.add(message);
        }
    }

    private void requireConfiguredPositive(List<String> errors, String propertyName, int value, String message) {
        if (!environment.containsProperty(propertyName) || value <= 0) {
            errors.add(message);
        }
    }

    private static void rejectJsonl(List<String> errors, String value, String message) {
        if (value != null && value.trim().toLowerCase(Locale.ROOT).endsWith(".jsonl")) {
            errors.add(message);
        }
    }
}
