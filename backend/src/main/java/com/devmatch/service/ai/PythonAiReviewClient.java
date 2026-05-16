package com.devmatch.service.ai;

import com.devmatch.config.AiReviewProperties;
import com.devmatch.entity.AiReviewEvaluation;
import com.devmatch.entity.Question;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PythonAiReviewClient {

    private static final Logger log = LoggerFactory.getLogger(PythonAiReviewClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    private final AiReviewProperties properties;
    private final AiReviewProviderSelector providerSelector;

    public Optional<String> generateFirstQuestion(Question question, String correctAnswer, String selectedAnswer) {
        if (providerSelector.selectProvider() != AiReviewProviderType.PYTHON) {
            return Optional.empty();
        }

        return request("/api/review/first-question", new PythonAiRequest(
                question.getContent(),
                question.getOptions(),
                correctAnswer,
                selectedAnswer,
                "",
                "",
                1,
                properties.python().model(),
                properties.python().temperature(),
                properties.python().maxTokens(),
                properties.python().numCtx(),
                properties.python().numThread()
        ));
    }

    public Optional<String> generateFollowUp(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String userAnswer,
            AiReviewEvaluation evaluation,
            int nextStep
    ) {
        if (providerSelector.selectProvider() != AiReviewProviderType.PYTHON) {
            return Optional.empty();
        }

        return request("/api/review/follow-up", new PythonAiRequest(
                question.getContent(),
                question.getOptions(),
                correctAnswer,
                selectedAnswer,
                userAnswer,
                evaluation.name(),
                nextStep,
                properties.python().model(),
                properties.python().temperature(),
                properties.python().maxTokens(),
                properties.python().numCtx(),
                properties.python().numThread()
        ));
    }

    public Optional<String> answerFreeQuestion(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String userQuestion
    ) {
        if (providerSelector.selectProvider() != AiReviewProviderType.PYTHON) {
            return Optional.empty();
        }

        return request("/api/review/free-question", new PythonAiRequest(
                question.getContent(),
                question.getOptions(),
                correctAnswer,
                selectedAnswer,
                userQuestion,
                "",
                1,
                properties.python().model(),
                properties.python().temperature(),
                properties.python().maxTokens(),
                properties.python().numCtx(),
                properties.python().numThread()
        ));
    }

    private Optional<String> request(String uri, PythonAiRequest request) {
        try {
            PythonAiResponse response = aiClient(properties.python().baseUrl())
                    .post()
                    .uri(uri)
                    .body(request)
                    .retrieve()
                    .body(PythonAiResponse.class);

            if (response == null || response.answer() == null || response.answer().isBlank()) {
                log.warn("Python AI returned an empty response. uri={}, baseUrl={}", uri, properties.python().baseUrl());
                return Optional.empty();
            }
            return Optional.of(response.answer().trim());
        } catch (RestClientException ex) {
            log.warn(
                    "Python AI request failed. uri={}, baseUrl={}, model={}, message={}",
                    uri,
                    properties.python().baseUrl(),
                    request.model(),
                    ex.getMessage()
            );
            return Optional.empty();
        }
    }

    private RestClient aiClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(readTimeout(properties.python().readTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private Duration readTimeout(int seconds) {
        return seconds <= 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }

    private record PythonAiRequest(
            String question,
            List<String> options,
            String correct_answer,
            String selected_answer,
            String user_answer,
            String evaluation,
            int step,
            String model,
            double temperature,
            int max_tokens,
            int num_ctx,
            int num_thread
    ) {
    }

    private record PythonAiResponse(
            String answer,
            String provider
    ) {
    }
}
