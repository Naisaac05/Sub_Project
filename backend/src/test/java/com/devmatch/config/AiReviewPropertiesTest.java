package com.devmatch.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewPropertiesTest {

    @Test
    void defaultsUseSmallPythonModelAnd4bOllamaFallback() {
        AiReviewProperties properties = new AiReviewProperties(
                true,
                AiReviewProperties.Provider.PYTHON,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                45
        );

        assertThat(properties.python().model()).isEqualTo("qwen3:1.7b");
        assertThat(properties.python().maxTokens()).isEqualTo(256);
        assertThat(properties.python().numCtx()).isEqualTo(1024);
        assertThat(properties.ollama().model()).isEqualTo("qwen3:4b-q4_K_M");
        assertThat(properties.ollama().maxTokens()).isEqualTo(256);
        assertThat(properties.ollama().numCtx()).isEqualTo(1024);
        assertThat(properties.python().readTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.ollama().readTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.ollama().maxConcurrentGenerations()).isEqualTo(1);
        assertThat(properties.evaluation().semanticEnabled()).isFalse();
    }

    @Test
    void streamingPropertiesHaveDefaults() {
        AiReviewProperties properties = new AiReviewProperties(
                true,
                AiReviewProperties.Provider.PYTHON,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                45
        );

        assertThat(properties.streamingEnabled()).isFalse();
        assertThat(properties.streamTimeoutSeconds()).isEqualTo(45);
    }
}
