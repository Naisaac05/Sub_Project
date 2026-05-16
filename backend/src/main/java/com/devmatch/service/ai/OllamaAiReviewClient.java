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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OllamaAiReviewClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaAiReviewClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final int MAX_CACHE_ENTRIES = 128;
    private static final int MAX_PROMPT_FIELD_LENGTH = 260;

    private final AiReviewProperties properties;
    private final AiReviewProviderSelector providerSelector;
    private final Map<String, String> responseCache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_CACHE_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            }
    );

    public Optional<String> generateFirstQuestion(Question question, String correctAnswer, String selectedAnswer) {
        if (!canUseOllama()) {
            return Optional.empty();
        }

        String prompt = """
                Korean mentor. Use the facts as truth.
                Output: 1 short question, max 2 sentences. No markdown. No <think>.

                Facts:
                %s

                Ask why the learner chose the selected answer and what concept they used.
                """.formatted(grounding(question, correctAnswer, selectedAnswer, "", null));

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
                Korean mentor. Use the facts as truth.
                Output: feedback + exactly 1 next question, max 3 short sentences. No markdown. No <think>.

                Facts:
                %s
                Step: %d

                If the learner is unsure, explain the missing concept briefly first.
                """.formatted(grounding(question, correctAnswer, selectedAnswer, userAnswer, evaluation), nextStep);

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
                Korean mentor. Use the facts as truth.
                Output: answer the learner's question, max 3 short sentences. No markdown. No <think>.

                Facts:
                %s

                Give a tiny concrete example only if it helps.
                """.formatted(grounding(question, correctAnswer, selectedAnswer, userQuestion, null));

        return generate(prompt);
    }

    private boolean canUseOllama() {
        return providerSelector.hasOllamaConfig()
                && providerSelector.selectProvider() == AiReviewProviderType.OLLAMA;
    }

    private Optional<String> generate(String prompt) {
        AiReviewProperties.Ollama ollama = properties.ollama();
        String cacheKey = cacheKey(ollama.model(), prompt);
        String cached = responseCache.get(cacheKey);
        if (cached != null) {
            log.debug("Ollama cache hit. model={}", ollama.model());
            return Optional.of(cached);
        }

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
            responseCache.put(cacheKey, answer);
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
        requestFactory.setReadTimeout(readTimeout(properties.ollama().readTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private Duration readTimeout(int seconds) {
        return seconds <= 0 ? Duration.ZERO : Duration.ofSeconds(seconds);
    }

    private String grounding(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String learnerText,
            AiReviewEvaluation evaluation
    ) {
        StringBuilder builder = new StringBuilder()
                .append("Question: ").append(shorten(question.getContent())).append('\n')
                .append("Selected: ").append(shorten(selectedAnswer)).append('\n')
                .append("Correct: ").append(shorten(correctAnswer)).append('\n')
                .append("Backend evidence: correct answer is authoritative; compare selected vs correct.");
        if (evaluation != null) {
            builder.append('\n').append("Rule evaluation: ").append(evaluation.name());
        }
        if (learnerText != null && !learnerText.isBlank()) {
            builder.append('\n').append("Learner: ").append(shorten(learnerText));
        }
        return builder.toString();
    }

    private String cacheKey(String model, String prompt) {
        return model + "\n" + prompt;
    }

    private String shorten(String value) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= MAX_PROMPT_FIELD_LENGTH
                ? compact
                : compact.substring(0, MAX_PROMPT_FIELD_LENGTH) + "...";
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
