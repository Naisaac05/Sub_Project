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
public class OllamaAiReviewClient {

    private final AiReviewProperties properties;
    private final AiReviewProviderSelector providerSelector;

    public Optional<String> generateFirstQuestion(Question question, String correctAnswer, String selectedAnswer) {
        if (providerSelector.selectProvider() != AiReviewProviderType.OLLAMA) {
            return Optional.empty();
        }

        String prompt = """
                You are a Korean programming mentor.
                Ask one short follow-up question for a learner who got a diagnostic test question wrong.
                Rules:
                - Korean only.
                - 2 sentences maximum.
                - Do not reveal the full answer yet.
                - Ask why they chose their answer and what concept they used.

                [Question]
                %s

                [Selected Answer]
                %s

                [Correct Answer]
                %s
                """.formatted(question.getContent(), selectedAnswer, correctAnswer);

        return generate(prompt);
    }

    public Optional<String> generateFollowUp(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String userAnswer,
            AiReviewEvaluation evaluation,
            int nextStep
    ) {
        if (providerSelector.selectProvider() != AiReviewProviderType.OLLAMA) {
            return Optional.empty();
        }

        String prompt = """
                You are a Korean programming mentor helping with a diagnostic test review.
                Give feedback on the learner's answer and ask exactly one next follow-up question.
                Rules:
                - Korean only.
                - 4 sentences maximum.
                - Be specific to the learner answer.
                - If the learner says they do not know, briefly explain the missing concept first.
                - Do not be too verbose.
                - Do not use markdown tables.

                [Question]
                %s

                [Options]
                %s

                [Selected Answer]
                %s

                [Correct Answer]
                %s

                [Learner Answer]
                %s

                [Rule Evaluation]
                %s

                [Follow-up Step]
                %d
                """.formatted(
                question.getContent(),
                formatOptions(question.getOptions()),
                selectedAnswer,
                correctAnswer,
                userAnswer,
                evaluation.name(),
                nextStep
        );

        return generate(prompt);
    }

    public Optional<String> answerFreeQuestion(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String userQuestion
    ) {
        if (providerSelector.selectProvider() != AiReviewProviderType.OLLAMA) {
            return Optional.empty();
        }

        String prompt = """
                You are a Korean programming mentor.
                Answer the learner's free question using the diagnostic test context.
                Rules:
                - Korean only.
                - 5 sentences maximum.
                - Explain with a small concrete example if helpful.
                - If the question is unrelated, gently connect it back to the concept.
                - Do not grade the learner.

                [Original Question]
                %s

                [Selected Answer]
                %s

                [Correct Answer]
                %s

                [Learner Free Question]
                %s
                """.formatted(question.getContent(), selectedAnswer, correctAnswer, userQuestion);

        return generate(prompt);
    }

    private Optional<String> generate(String prompt) {
        AiReviewProperties.Ollama ollama = properties.ollama();
        try {
            OllamaGenerateResponse response = RestClient.create(ollama.baseUrl())
                    .post()
                    .uri("/api/generate")
                    .body(new OllamaGenerateRequest(
                            ollama.model(),
                            prompt,
                            false,
                            new OllamaOptions(ollama.temperature(), ollama.maxTokens())
                    ))
                    .retrieve()
                    .body(OllamaGenerateResponse.class);

            if (response == null || response.response() == null || response.response().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(response.response().trim());
        } catch (RestClientException ex) {
            return Optional.empty();
        }
    }

    private String formatOptions(List<String> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < options.size(); i++) {
            builder.append(i + 1).append(". ").append(options.get(i)).append('\n');
        }
        return builder.toString();
    }

    private record OllamaGenerateRequest(
            String model,
            String prompt,
            boolean stream,
            OllamaOptions options
    ) {
    }

    private record OllamaOptions(
            double temperature,
            int num_predict
    ) {
    }

    private record OllamaGenerateResponse(
            String response
    ) {
    }
}
