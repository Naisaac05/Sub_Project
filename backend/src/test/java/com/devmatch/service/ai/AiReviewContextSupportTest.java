package com.devmatch.service.ai;

import com.devmatch.entity.*;
import com.devmatch.exception.TestNotFoundException;
import com.devmatch.repository.AiReviewMessageRepository;
import com.devmatch.repository.AiReviewSessionRepository;
import com.devmatch.repository.TestAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReviewContextSupportTest {

    @Mock private AiReviewSessionRepository sessionRepository;
    @Mock private TestAnswerRepository testAnswerRepository;
    @Mock private AiReviewMessageRepository messageRepository;

    @InjectMocks
    private AiReviewContextSupport contextSupport;

    private User user;
    private AiReviewSession session;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .email("learner@devmatch.com")
                .password("encoded")
                .name("learner")
                .role(Role.MENTEE)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        session = AiReviewSession.builder()
                .user(user)
                .courseKey("java")
                .status(AiReviewStatus.IN_PROGRESS)
                .build();
        ReflectionTestUtils.setField(session, "id", 20L);
    }

    @Test
    void findOwnedSession_whenAuthorized_shouldReturnSession() {
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));

        AiReviewSession found = contextSupport.findOwnedSession(1L, 20L);

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(20L);
    }

    @Test
    void findOwnedSession_whenNotAuthorized_shouldThrowException() {
        when(sessionRepository.findById(20L)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> contextSupport.findOwnedSession(999L, 20L))
                .isInstanceOf(TestNotFoundException.class);
    }

    @Test
    void optionAt_whenValid_shouldReturnOption() {
        Question question = Question.builder()
                .content("Question")
                .options(List.of("Option A", "Option B"))
                .correctAnswer(0)
                .build();

        String option = contextSupport.optionAt(question, 1);
        assertThat(option).isEqualTo("Option B");

        String invalid = contextSupport.optionAt(question, 5);
        assertThat(invalid).isEqualTo("미응답");
    }
}
