package com.devmatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai-review")
public record AiReviewProperties(
        boolean enabled,
        Provider provider,
        OpenAi openai,
        PythonAi python,
        Ollama ollama,
        RuleBased ruleBased,
        Limits limits,
        Evaluation evaluation,
        Degraded degraded,
        boolean streamingEnabled,
        int streamTimeoutSeconds,
        String candidatesPath,
        String autoCandidatesPath
) {
    public AiReviewProperties {
        if (provider == null) {
            provider = Provider.PYTHON;
        }
        if (openai == null) {
            openai = new OpenAi("", "gpt-4.1-mini", "https://api.openai.com/v1/responses", 0.2);
        }
        if (python == null) {
            python = new PythonAi(true, "http://localhost:8001", "qwen3:1.7b", 0.2, 256, 1024, 4, 30, "");
        }
        if (ollama == null) {
            ollama = new Ollama(true, "qwen3:4b-q4_K_M", "http://localhost:11434", 0.2, 256, 1024, 4, 30, 1);
        }
        if (ruleBased == null) {
            ruleBased = new RuleBased(true);
        }
        if (limits == null) {
            limits = new Limits(3, 10, 700);
        }
        if (evaluation == null) {
            evaluation = new Evaluation(false);
        }
        if (degraded == null) {
            degraded = new Degraded(false);
        }
        if (streamTimeoutSeconds == 0) {
            streamTimeoutSeconds = 45;
        }
        if (candidatesPath == null) {
            candidatesPath = "";
        }
        if (autoCandidatesPath == null) {
            autoCandidatesPath = "";
        }
    }

    public AiReviewProperties(
            boolean enabled,
            Provider provider,
            OpenAi openai,
            PythonAi python,
            Ollama ollama,
            RuleBased ruleBased,
            Limits limits,
            Evaluation evaluation,
            Degraded degraded,
            boolean streamingEnabled,
            int streamTimeoutSeconds
    ) {
        this(
                enabled,
                provider,
                openai,
                python,
                ollama,
                ruleBased,
                limits,
                evaluation,
                degraded,
                streamingEnabled,
                streamTimeoutSeconds,
                "",
                ""
        );
    }

    public enum Provider {
        AUTO,
        OPENAI,
        PYTHON,
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

    public record PythonAi(
            boolean enabled,
            String baseUrl,
            String model,
            double temperature,
            int maxTokens,
            int numCtx,
            int numThread,
            int readTimeoutSeconds,
            String serviceToken
    ) {
    }

    public record Ollama(
            boolean enabled,
            String model,
            String baseUrl,
            double temperature,
            int maxTokens,
            int numCtx,
            int numThread,
            int readTimeoutSeconds,
            int maxConcurrentGenerations
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

    public record Evaluation(
            boolean semanticEnabled
    ) {
    }

    public record Degraded(
            boolean streamingOff
    ) {
    }
}
