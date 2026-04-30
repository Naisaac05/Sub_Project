package com.devmatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-review")
public record AiReviewProperties(
        boolean enabled,
        Provider provider,
        OpenAi openai,
        Ollama ollama,
        RuleBased ruleBased,
        Limits limits
) {
    public AiReviewProperties {
        if (provider == null) {
            provider = Provider.AUTO;
        }
        if (openai == null) {
            openai = new OpenAi("", "gpt-4.1-mini", "https://api.openai.com/v1/responses", 0.2);
        }
        if (ollama == null) {
            ollama = new Ollama(true, "qwen2.5:1.5b", "http://localhost:11434", 0.2, 200);
        }
        if (ruleBased == null) {
            ruleBased = new RuleBased(true);
        }
        if (limits == null) {
            limits = new Limits(3, 10, 700);
        }
    }

    public enum Provider {
        AUTO,
        OPENAI,
        OLLAMA,
        RULE_BASED
    }

    public record OpenAi(
            String apiKey,
            String model,
            String baseUrl,
            double temperature
    ) {
    }

    public record Ollama(
            boolean enabled,
            String model,
            String baseUrl,
            double temperature,
            int maxTokens
    ) {
    }

    public record RuleBased(
            boolean enabled
    ) {
    }

    public record Limits(
            int maxQuestionsPerWrongAnswer,
            int maxQuestionsPerSession,
            int maxUserAnswerLength
    ) {
    }
}
