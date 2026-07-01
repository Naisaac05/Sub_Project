package com.devmatch.config;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesScanConfig.class);

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
                null,
                false,
                45
        );

        assertThat(properties.python().model()).isEqualTo("exaone3.5:2.4b");
        assertThat(properties.python().maxTokens()).isEqualTo(256);
        assertThat(properties.python().numCtx()).isEqualTo(1024);
        assertThat(properties.ollama().model()).isEqualTo("exaone3.5:2.4b");
        assertThat(properties.ollama().maxTokens()).isEqualTo(256);
        assertThat(properties.ollama().numCtx()).isEqualTo(1024);
        assertThat(properties.python().readTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.ollama().readTimeoutSeconds()).isEqualTo(30);
        assertThat(properties.ollama().maxConcurrentGenerations()).isEqualTo(1);
        assertThat(properties.evaluation().semanticEnabled()).isFalse();
        assertThat(properties.degraded().streamingOff()).isFalse();
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
                null,
                false,
                45
        );

        assertThat(properties.streamingEnabled()).isFalse();
        assertThat(properties.streamTimeoutSeconds()).isEqualTo(45);
    }

    @Test
    void degradedPropertiesHaveDefaults() {
        AiReviewProperties properties = new AiReviewProperties(
                true,
                AiReviewProperties.Provider.PYTHON,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                45
        );

        assertThat(properties.degraded().streamingOff()).isFalse();
    }

    @Test
    void bindsFromConfigurationPropertiesScanWhenMultipleConstructorsExist() {
        contextRunner
                .withPropertyValues(
                        "app.ai-review.enabled=true",
                        "app.ai-review.provider=PYTHON",
                        "app.ai-review.python.model=exaone3.5:2.4b",
                        "app.ai-review.streaming-enabled=true",
                        "app.ai-review.stream-timeout-seconds=60"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AiReviewProperties properties = context.getBean(AiReviewProperties.class);
                    assertThat(properties.enabled()).isTrue();
                    assertThat(properties.provider()).isEqualTo(AiReviewProperties.Provider.PYTHON);
                    assertThat(properties.python().model()).isEqualTo("exaone3.5:2.4b");
                    assertThat(properties.streamingEnabled()).isTrue();
                    assertThat(properties.streamTimeoutSeconds()).isEqualTo(60);
                });
    }

    @Test
    void applicationYamlUsesTheProductionOllamaModelDefaults() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));

        assertThat(sources.get(0).getProperty("app.ai-review.python.model"))
                .isEqualTo("${PYTHON_AI_MODEL:exaone3.5:2.4b}");
        assertThat(sources.get(0).getProperty("app.ai-review.ollama.model"))
                .isEqualTo("${OLLAMA_MODEL:exaone3.5:2.4b}");
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan(basePackageClasses = AiReviewProperties.class)
    static class PropertiesScanConfig {
    }
}
