// 🧪 테스트 전용: env ON 상태에서 TestResetAiReviewController 동작 검증
package com.devmatch.controller;

import com.devmatch.entity.AiReviewMessage;
import com.devmatch.entity.AiReviewMessageRole;
import com.devmatch.entity.AiReviewSession;
import com.devmatch.entity.Difficulty;
import com.devmatch.entity.Role;
import com.devmatch.entity.TestResult;
import com.devmatch.entity.User;
import com.devmatch.repository.AiReviewMessageRepository;
import com.devmatch.repository.AiReviewSessionRepository;
import com.devmatch.repository.TestRepository;
import com.devmatch.repository.TestResultRepository;
import com.devmatch.repository.UserRepository;
import com.devmatch.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.ai-review.test-reset.enabled=true")
class TestResetAiReviewControllerEnabledTest {

    @Autowired private MockMvc mvc;
    @Autowired private AiReviewSessionRepository sessionRepository;
    @Autowired private AiReviewMessageRepository messageRepository;
    @Autowired private TestResultRepository testResultRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TestRepository testRepository;

    private CustomUserDetails principal(Long userId) {
        return new CustomUserDetails(userId, "u" + userId + "@devmatch.com", Role.MENTEE);
    }

    @Test
    void resetSession_whenEnabled_deletesOwnSessionAndMessages() throws Exception {
        // Given
        User user = persistUser();
        TestResult result = persistTestResult(user);
        AiReviewSession session = persistSession(user, result);
        persistMessage(session);
        persistMessage(session);

        // When
        mvc.perform(post("/api/ai-review/test-results/" + result.getId() + "/session/reset")
                        .with(user(principal(user.getId()))))
                .andExpect(status().isOk());

        // Then
        assertThat(sessionRepository.findById(session.getId())).isEmpty();
        assertThat(messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId())).isEmpty();
    }

    @Test
    void resetSession_whenOtherUsersTestResult_returnsForbidden() throws Exception {
        User owner = persistUser();
        User attacker = persistUser();
        TestResult result = persistTestResult(owner);
        persistSession(owner, result);

        mvc.perform(post("/api/ai-review/test-results/" + result.getId() + "/session/reset")
                        .with(user(principal(attacker.getId()))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void resetSession_whenNoSessionExists_returnsOkNoop() throws Exception {
        User user = persistUser();
        TestResult result = persistTestResult(user);

        mvc.perform(post("/api/ai-review/test-results/" + result.getId() + "/session/reset")
                        .with(user(principal(user.getId()))))
                .andExpect(status().isOk());
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private User persistUser() {
        return userRepository.save(User.builder()
                .email("user" + System.nanoTime() + "@devmatch.com")
                .password("pw")
                .name("테스터")
                .build());
    }

    private TestResult persistTestResult(User user) {
        com.devmatch.entity.Test test = testRepository.save(
                com.devmatch.entity.Test.builder()
                        .title("테스트")
                        .category("JAVA")
                        .difficulty(Difficulty.BEGINNER)
                        .timeLimit(60)
                        .passingScore(60)
                        .questionCount(10)
                        .build());

        return testResultRepository.save(TestResult.builder()
                .user(user)
                .test(test)
                .totalScore(100)
                .correctCount(10)
                .passed(true)
                .submittedAt(LocalDateTime.now())
                .build());
    }

    private AiReviewSession persistSession(User user, TestResult result) {
        return sessionRepository.save(AiReviewSession.builder()
                .user(user)
                .testResult(result)
                .courseKey("JAVA-BASIC")
                .build());
    }

    private AiReviewMessage persistMessage(AiReviewSession session) {
        return messageRepository.save(AiReviewMessage.builder()
                .session(session)
                .role(AiReviewMessageRole.USER)
                .content("테스트 메시지")
                .build());
    }
}
