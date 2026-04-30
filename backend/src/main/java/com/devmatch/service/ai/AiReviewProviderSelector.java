package com.devmatch.service.ai;

import com.devmatch.config.AiReviewProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AiReviewProviderSelector {

    private final AiReviewProperties properties;

    public AiReviewProviderType selectProvider() {
        if (!properties.enabled()) {
            return AiReviewProviderType.RULE_BASED;
        }

        return switch (properties.provider()) {
            case OPENAI -> hasOpenAiKey() ? AiReviewProviderType.OPENAI : AiReviewProviderType.RULE_BASED;
            case PYTHON -> hasPythonAiConfig() ? AiReviewProviderType.PYTHON : AiReviewProviderType.RULE_BASED;
            case OLLAMA -> hasOllamaConfig() ? AiReviewProviderType.OLLAMA : AiReviewProviderType.RULE_BASED;
            case RULE_BASED -> AiReviewProviderType.RULE_BASED;
            case AUTO -> {
                if (hasOpenAiKey()) {
                    yield AiReviewProviderType.OPENAI;
                }
                if (hasPythonAiConfig()) {
                    yield AiReviewProviderType.PYTHON;
                }
                yield hasOllamaConfig() ? AiReviewProviderType.OLLAMA : AiReviewProviderType.RULE_BASED;
            }
        };
    }

    public boolean hasOpenAiKey() {
        String apiKey = properties.openai().apiKey();
        return apiKey != null
                && !apiKey.isBlank()
                && !apiKey.equals("your-openai-api-key");
    }

    public boolean hasPythonAiConfig() {
        return properties.python() != null
                && properties.python().enabled()
                && properties.python().baseUrl() != null
                && !properties.python().baseUrl().isBlank()
                && properties.python().model() != null
                && !properties.python().model().isBlank();
    }

    public boolean hasOllamaConfig() {
        return properties.ollama() != null
                && properties.ollama().enabled()
                && properties.ollama().model() != null
                && !properties.ollama().model().isBlank()
                && properties.ollama().baseUrl() != null
                && !properties.ollama().baseUrl().isBlank();
    }
}
