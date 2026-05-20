package com.devmatch.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    @Test
    void corsAllowsPatchForAdminCandidateReview() {
        CorsConfig config = new CorsConfig();

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/admin/ai-review/candidates/v2/1/review");
        CorsConfiguration cors = config.corsConfigurationSource()
                .getCorsConfiguration(request);

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedMethods()).contains("PATCH");
    }
}
