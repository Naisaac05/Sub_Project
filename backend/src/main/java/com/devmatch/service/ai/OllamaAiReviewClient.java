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
public class OllamaAiReviewClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiReviewClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    private final AiReviewProperties properties;
    private final AiReviewProviderSelector providerSelector;

    public Optional<String> generateFirstQuestion(Question question, String correctAnswer, String selectedAnswer) {
        if (!canUseOllama()) {
            return Optional.empty();
        }

        String prompt = """
                You are a Korean programming mentor.
                Ask one short follow-up question for a learner who got a diagnostic test question wrong.
                Rules:
                - Korean only.
                - 2 sentences maximum.
                - Do not write reasoning steps or <think> tags.
                - Do not reveal the full answer yet.
                - Ask why they chose their answer and what concept they used.
                - Ask exactly one question.
                - Stop after the question.

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
        if (!canUseOllama()) {
            return Optional.empty();
        }

        String prompt = """
                You are a Korean programming mentor helping with a diagnostic test review.
                Give feedback on the learner's answer and ask exactly one next follow-up question.
                Rules:
                - Korean only.
                - 3 sentences maximum.
                - Do not write reasoning steps or <think> tags.
                - Be specific to the learner answer.
                - If the learner says they do not know, briefly explain the missing concept first.
                - Do not be too verbose.
                - Do not use markdown tables.
                - Ask exactly one next question.
                - Stop after the next question.

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
        if (!canUseOllama()) {
            return Optional.empty();
        }

        String prompt = """
                You are a Korean programming mentor.
                Answer the learner's free question using the diagnostic test context.
                Rules:
                - Korean only.
                - 3 sentences maximum.
                - Do not write reasoning steps or <think> tags.
                - Explain with a small concrete example if helpful.
                - If the question is unrelated, gently connect it back to the concept.
                - Do not grade the learner.
                - Do not ask more than one follow-up question.
                - Stop after the answer.

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

    private boolean canUseOllama() {
        return providerSelector.hasOllamaConfig()
                && providerSelector.selectProvider() == AiReviewProviderType.OLLAMA;
    }

    private Optional<String> generate(String prompt) {
        AiReviewProperties.Ollama ollama = properties.ollama();
        try {
            OllamaGenerateResponse response = aiClient(ollama.baseUrl())
                    .post()
                    .uri("/api/generate")
                    .body(new OllamaGenerateRequest(
                            ollama.model(),
                            prompt,
                            false,
                            false,
                            new OllamaOptions(
                                    ollama.temperature(),
                                    Math.min(ollama.maxTokens(), 1000),
                                    ollama.numCtx(),
                                    ollama.numThread(),
                                    1.25,
                                    128,
                                    List.of("\n\n\n", "[Question]", "[Original Question]", "[Learner Free Question]")
                            )
                    ))
                    .retrieve()
                    .body(OllamaGenerateResponse.class);

            if (response == null || response.response() == null || response.response().isBlank()) {
                log.warn("Ollama returned an empty response. baseUrl={}, model={}", ollama.baseUrl(), ollama.model());
                return Optional.empty();
            }
            String answer = compactAnswer(stripThinking(response.response().trim()));
            if (!containsKorean(answer)) {
                log.warn("Ollama response did not contain Korean text. baseUrl={}, model={}", ollama.baseUrl(), ollama.model());
                return Optional.empty();
            }
            return Optional.of(answer);
        } catch (RestClientException ex) {
            log.warn(
                    "Ollama request failed. baseUrl={}, model={}, message={}",
                    ollama.baseUrl(),
                    ollama.model(),
                    ex.getMessage()
            );
            return Optional.empty();
        }
    }

    private RestClient aiClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.ollama().readTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private String compactAnswer(String answer) {
        if (answer.isBlank()) {
            return answer;
        }
        String[] paragraphs = answer.split("\\R\\s*\\R");
        StringBuilder builder = new StringBuilder();
        for (String paragraph : paragraphs) {
            String normalized = paragraph.trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (builder.indexOf(normalized) >= 0) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("\n\n");
            }
            builder.append(normalized);
        }
        return limitSentences(builder.toString(), 3);
    }

    private String limitSentences(String text, int sentenceLimit) {
        int sentenceCount = 0;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            builder.append(ch);
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                sentenceCount++;
                if (sentenceCount >= sentenceLimit) {
                    break;
                }
            }
        }
        return builder.toString().trim();
    }

    private String stripThinking(String answer) {
        String cleaned = answer;
        while (cleaned.contains("<think>") && cleaned.contains("</think>")) {
            int start = cleaned.indexOf("<think>");
            int end = cleaned.indexOf("</think>", start);
            if (end < 0) {
                break;
            }
            cleaned = cleaned.substring(0, start) + cleaned.substring(end + "</think>".length());
        }
        return cleaned.trim();
    }

    private boolean containsKorean(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '\uAC00' && ch <= '\uD7A3') {
                return true;
            }
        }
        return false;
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
            boolean think,
            OllamaOptions options
    ) {
    }

    private record OllamaOptions(
            double temperature,
            int num_predict,
            int num_ctx,
            int num_thread,
            double repeat_penalty,
            int repeat_last_n,
            List<String> stop
    ) {
    }

    private record OllamaGenerateResponse(
            String response
    ) {
    }
}
