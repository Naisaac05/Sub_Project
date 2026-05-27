package com.devmatch.repository;

import com.devmatch.config.JpaAuditingConfig;
import com.devmatch.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class AiReviewMessageRepositoryTest {

    @Autowired private AiReviewMessageRepository messageRepository;
    @Autowired private AiReviewSessionRepository sessionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TestRepository testRepository;
    @Autowired private TestResultRepository testResultRepository;

    @Test
    void deleteBySessionId_removesAllMessagesForGivenSession_andLeavesOthers() {
        // Given: 두 세션, 각각 메시지 보유
        AiReviewSession sessionA = persistedSession();
        AiReviewSession sessionB = persistedSession();
        persistedMessage(sessionA);
        persistedMessage(sessionA);
        persistedMessage(sessionB);

        // When
        long deleted = messageRepository.deleteBySessionId(sessionA.getId());

        // Then
        assertThat(deleted).isEqualTo(2);
        assertThat(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionA.getId())).isEmpty();
        assertThat(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionB.getId())).hasSize(1);
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────────────────

    private AiReviewSession persistedSession() {
        User user = userRepository.save(User.builder()
                .email("user" + System.nanoTime() + "@test.com")
                .password("pw")
                .name("테스터")
                .build());

        com.devmatch.entity.Test test = testRepository.save(
                com.devmatch.entity.Test.builder()
                        .title("테스트")
                        .category("JAVA")
                        .difficulty(Difficulty.BEGINNER)
                        .timeLimit(60)
                        .passingScore(60)
                        .questionCount(10)
                        .build());

        TestResult testResult = testResultRepository.save(TestResult.builder()
                .user(user)
                .test(test)
                .totalScore(100)
                .correctCount(10)
                .passed(true)
                .submittedAt(LocalDateTime.now())
                .build());

        return sessionRepository.save(AiReviewSession.builder()
                .user(user)
                .testResult(testResult)
                .courseKey("JAVA-BASIC")
                .build());
    }

    private AiReviewMessage persistedMessage(AiReviewSession session) {
        return messageRepository.save(AiReviewMessage.builder()
                .session(session)
                .role(AiReviewMessageRole.USER)
                .content("테스트 메시지")
                .build());
    }
}
