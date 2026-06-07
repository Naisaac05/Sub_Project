// 🧪 테스트 전용: env OFF 시 reset 엔드포인트가 등록 안 되는지 회귀 가드
package com.devmatch.controller;

import com.devmatch.entity.Role;
import com.devmatch.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai-review.test-reset.enabled=false")
class TestResetAiReviewControllerDisabledTest {

    @Autowired private MockMvc mvc;

    private CustomUserDetails menteePrincipal() {
        return new CustomUserDetails(1L, "learner@devmatch.com", Role.MENTEE);
    }

    @Test
    void resetSession_whenDisabled_returnsNotFound() throws Exception {
        mvc.perform(post("/api/ai-review/test-results/100/session/reset")
                        .with(user(menteePrincipal())))
                .andExpect(status().isNotFound());
    }
}
