package com.devmatch.controller;

import com.devmatch.entity.Role;
import com.devmatch.security.CustomUserDetails;
import com.devmatch.service.ai.AiReviewStreamingService;
import com.devmatch.service.ai.RuleBasedAiReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiReviewControllerTest {

    @Autowired
    private MockMvc mvc;


    @Autowired
    private ObjectMapper objectMapper;


    @MockitoBean
    private RuleBasedAiReviewService aiReviewService;

    @MockitoBean
    private AiReviewStreamingService aiReviewStreamingService;

    private CustomUserDetails menteePrincipal() {
        return new CustomUserDetails(1L, "learner@devmatch.com", Role.MENTEE);
    }

    @Test
    void streamAnswer_shouldReturnSseEmitter() throws Exception {
        // Given
        Map<String, Object> request = Map.of(
                "answer", "My answer",
                "mode", "CHECK_ANSWER",
                "questionId", 100L
        );
        SseEmitter mockEmitter = new SseEmitter();

        when(aiReviewStreamingService.streamAnswer(
                eq(1L), eq(20L), eq("My answer"), eq("CHECK_ANSWER"), eq(100L), any()
        )).thenReturn(mockEmitter);


        // When & Then
        mvc.perform(post("/api/ai-review/sessions/20/messages/stream")
                        .with(user(menteePrincipal()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }
}
