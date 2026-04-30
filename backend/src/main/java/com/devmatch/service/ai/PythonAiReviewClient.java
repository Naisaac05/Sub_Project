package com.devmatch.service.ai;

import com.devmatch.config.AiReviewProperties;
import com.devmatch.entity.AiReviewEvaluation;
import com.devmatch.entity.Question;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PythonAiReviewClient {

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
                properties.python().maxTokens()
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
                properties.python().maxTokens()
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
                properties.python().maxTokens()
        ));
    }

    private Optional<String> request(String uri, PythonAiRequest request) {
        try {
            PythonAiResponse response = RestClient.create(properties.python().baseUrl())
                    .post()
                    .uri(uri)
                    .body(request)
                    .retrieve()
                    .body(PythonAiResponse.class);

            if (response == null || response.answer() == null || response.answer().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(response.answer().trim());
        } catch (RestClientException ex) {
            return Optional.empty();
        }
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
            int max_tokens
    ) {
    }

    private record PythonAiResponse(
            String answer,
            String provider
    ) {
    }
}
