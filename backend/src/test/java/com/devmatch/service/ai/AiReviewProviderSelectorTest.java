package com.devmatch.service.ai;

import com.devmatch.config.AiReviewProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiReviewProviderSelectorTest {

    @Test
    void autoPrefersPythonBeforeOllamaSoSmallModelIsPrimary() {
        AiReviewProperties properties = new AiReviewProperties(
                true,
                AiReviewProperties.Provider.AUTO,
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

        AiReviewProviderSelector selector = new AiReviewProviderSelector(properties);

        assertThat(selector.selectProvider()).isEqualTo(AiReviewProviderType.PYTHON);
        assertThat(selector.canUseOllamaFallback()).isTrue();
    }
}
