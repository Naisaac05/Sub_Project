package com.devmatch.service.ai;

import com.devmatch.dto.aireview.*;
import com.devmatch.entity.*;
import com.devmatch.exception.TestNotFoundException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleBasedAiReviewService {

    private static final int MAX_QUESTIONS_PER_WRONG_ANSWER = 3;
    private static final int MAX_AI_MESSAGE_LENGTH = 1800;

    private final UserRepository userRepository;
    private final TestResultRepository testResultRepository;
    private final TestAnswerRepository testAnswerRepository;
    private final AiReviewSessionRepository sessionRepository;
    private final AiReviewMessageRepository messageRepository;
    private final AiReviewProviderSelector providerSelector;
    private final OllamaAiReviewClient ollamaAiReviewClient;

    @Transactional
    public AiReviewSessionResponse startReview(Long userId, Long testResultId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다."));
        TestResult result = findOwnedResult(userId, testResultId);

        Optional<AiReviewSession> previous =
                sessionRepository.findTopByUserIdAndTestResultIdOrderByCreatedAtDesc(userId, testResultId);
        if (previous.isPresent()) {
            AiReviewSession session = previous.get();
            if (messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).isEmpty()) {
                askFirstQuestion(session, wrongAnswers(testResultId));
            }
            return response(session);
        }

        AiReviewSession session = sessionRepository.save(AiReviewSession.builder()
                .user(user)
                .testResult(result)
                .courseKey(result.getTest().getCategory())
                .status(AiReviewStatus.IN_PROGRESS)
                .build());

        List<TestAnswer> wrongAnswers = wrongAnswers(testResultId);
        if (wrongAnswers.isEmpty()) {
            session.complete("틀린 문제가 없어 AI 복습이 필요하지 않습니다.", "");
        } else {
            askFirstQuestion(session, wrongAnswers);
        }

        return response(session);
    }

    public AiReviewSessionResponse getSession(Long userId, Long sessionId) {
        AiReviewSession session = findOwnedSession(userId, sessionId);
        return response(session);
    }

    @Transactional
    public AiReviewSubmitResponse submitAnswer(Long userId, Long sessionId, String answer, String modeValue) {
        AiReviewSession session = findOwnedSession(userId, sessionId);
        if (session.getStatus() == AiReviewStatus.COMPLETED) {
            return new AiReviewSubmitResponse(
                    "COMPLETED",
                    "이미 완료된 복습입니다.",
                    null,
                    true,
                    session.getSummary(),
                    messages(session)
            );
        }

        AiReviewMessageMode mode = parseMode(modeValue);
        String normalizedAnswer = answer == null ? "" : answer.trim();

        AiReviewMessage lastAiMessage = messageRepository
                .findTopBySessionIdAndRoleOrderByCreatedAtDesc(sessionId, AiReviewMessageRole.AI)
                .orElseThrow(() -> new TestNotFoundException("진행 중인 복습 질문이 없습니다."));
        Question currentQuestion = lastAiMessage.getQuestion();
        List<TestAnswer> wrongAnswers = wrongAnswers(session.getTestResult().getId());

        if (mode == AiReviewMessageMode.NEXT_QUESTION) {
            return moveToNextQuestion(session, wrongAnswers, currentQuestion);
        }
        if (normalizedAnswer.isBlank()) {
            throw new TestNotFoundException("답변이나 질문을 입력해주세요.");
        }
        if (mode == AiReviewMessageMode.FREE_QUESTION) {
            return answerFreeQuestion(session, wrongAnswers, currentQuestion, normalizedAnswer);
        }

        AiReviewEvaluation evaluation = evaluateAnswer(currentQuestion, normalizedAnswer);
        messageRepository.save(AiReviewMessage.builder()
                .session(session)
                .question(currentQuestion)
                .role(AiReviewMessageRole.USER)
                .mode(AiReviewMessageMode.CHECK_ANSWER)
                .content(normalizedAnswer)
                .evaluation(evaluation)
                .build());

        long aiQuestionCount = messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                sessionId,
                currentQuestion.getId(),
                AiReviewMessageRole.AI,
                List.of(
                        AiReviewMessageMode.CHECK_QUESTION,
                        AiReviewMessageMode.EXPLANATION,
                        AiReviewMessageMode.NEXT_QUESTION
                )
        );

        String feedback = feedback(evaluation);
        String nextQuestion = null;

        if (evaluation == AiReviewEvaluation.UNDERSTOOD || aiQuestionCount >= MAX_QUESTIONS_PER_WRONG_ANSWER) {
            Optional<TestAnswer> nextWrongAnswer = nextWrongAnswer(wrongAnswers, currentQuestion);
            if (nextWrongAnswer.isPresent()) {
                Question question = nextWrongAnswer.get().getQuestion();
                nextQuestion = ollamaAiReviewClient.generateFirstQuestion(
                        question,
                        optionAt(question, question.getCorrectAnswer()),
                        optionAt(question, nextWrongAnswer.get().getSelectedAnswer())
                ).orElse(buildQuestion(question, 1));
                saveAiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, withFeedback(feedback, nextQuestion));
            } else {
                String summary = buildSummary(session, wrongAnswers);
                session.complete(summary, weaknessTags(wrongAnswers));
            }
        } else {
            int nextStep = (int) aiQuestionCount + 1;
            Optional<TestAnswer> currentWrongAnswer = findWrongAnswer(wrongAnswers, currentQuestion);
            String selectedAnswer = currentWrongAnswer
                    .map(testAnswer -> optionAt(currentQuestion, testAnswer.getSelectedAnswer()))
                    .orElse("");
            nextQuestion = ollamaAiReviewClient.generateFollowUp(
                    currentQuestion,
                    optionAt(currentQuestion, currentQuestion.getCorrectAnswer()),
                    selectedAnswer,
                    normalizedAnswer,
                    evaluation,
                    nextStep
            ).orElse(withFeedback(feedback, buildQuestion(currentQuestion, nextStep)));
            saveAiMessage(session, currentQuestion, AiReviewMessageMode.EXPLANATION, nextQuestion);
        }

        boolean completed = session.getStatus() == AiReviewStatus.COMPLETED;
        return new AiReviewSubmitResponse(
                evaluation.name(),
                feedback,
                nextQuestion,
                completed,
                session.getSummary(),
                messages(session)
        );
    }

    private AiReviewSubmitResponse answerFreeQuestion(
            AiReviewSession session,
            List<TestAnswer> wrongAnswers,
            Question currentQuestion,
            String questionText
    ) {
        messageRepository.save(AiReviewMessage.builder()
                .session(session)
                .question(currentQuestion)
                .role(AiReviewMessageRole.USER)
                .mode(AiReviewMessageMode.FREE_QUESTION)
                .content(questionText)
                .build());

        Optional<TestAnswer> currentWrongAnswer = findWrongAnswer(wrongAnswers, currentQuestion);
        String selectedAnswer = currentWrongAnswer
                .map(testAnswer -> optionAt(currentQuestion, testAnswer.getSelectedAnswer()))
                .orElse("");
        String answer = ollamaAiReviewClient.answerFreeQuestion(
                currentQuestion,
                optionAt(currentQuestion, currentQuestion.getCorrectAnswer()),
                selectedAnswer,
                questionText
        ).orElse(buildFreeQuestionFallback(currentQuestion));

        saveAiMessage(session, currentQuestion, AiReviewMessageMode.FREE_ANSWER, answer);

        return new AiReviewSubmitResponse(
                "FREE_QUESTION",
                answer,
                null,
                false,
                session.getSummary(),
                messages(session)
        );
    }

    private AiReviewSubmitResponse moveToNextQuestion(
            AiReviewSession session,
            List<TestAnswer> wrongAnswers,
            Question currentQuestion
    ) {
        Optional<TestAnswer> nextWrongAnswer = nextWrongAnswer(wrongAnswers, currentQuestion);
        if (nextWrongAnswer.isPresent()) {
            TestAnswer nextAnswer = nextWrongAnswer.get();
            Question question = nextAnswer.getQuestion();
            String nextQuestion = ollamaAiReviewClient.generateFirstQuestion(
                    question,
                    optionAt(question, question.getCorrectAnswer()),
                    optionAt(question, nextAnswer.getSelectedAnswer())
            ).orElse(buildQuestion(question, 1));
            saveAiMessage(session, question, AiReviewMessageMode.NEXT_QUESTION, nextQuestion);

            return new AiReviewSubmitResponse(
                    "NEXT_QUESTION",
                    "다음 틀린 문제로 이동했어요.",
                    nextQuestion,
                    false,
                    session.getSummary(),
                    messages(session)
            );
        }

        String summary = buildSummary(session, wrongAnswers);
        session.complete(summary, weaknessTags(wrongAnswers));
        saveAiMessage(session, currentQuestion, AiReviewMessageMode.SYSTEM_SUMMARY, summary);
        return new AiReviewSubmitResponse(
                "COMPLETED",
                "복습을 마무리했어요.",
                null,
                true,
                session.getSummary(),
                messages(session)
        );
    }

    private void askFirstQuestion(AiReviewSession session, List<TestAnswer> wrongAnswers) {
        if (!wrongAnswers.isEmpty()) {
            TestAnswer firstAnswer = wrongAnswers.get(0);
            Question firstQuestion = firstAnswer.getQuestion();
            String question = ollamaAiReviewClient.generateFirstQuestion(
                    firstQuestion,
                    optionAt(firstQuestion, firstQuestion.getCorrectAnswer()),
                    optionAt(firstQuestion, firstAnswer.getSelectedAnswer())
            ).orElse(buildQuestion(firstQuestion, 1));
            saveAiMessage(session, firstQuestion, AiReviewMessageMode.CHECK_QUESTION, question);
        }
    }

    private void saveAiQuestion(AiReviewSession session, Question question, String content) {
        saveAiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, content);
    }

    private void saveAiMessage(AiReviewSession session, Question question, AiReviewMessageMode mode, String content) {
        messageRepository.save(AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.AI)
                .mode(mode)
                .content(limitAiMessage(content))
                .build());
    }

    private String limitAiMessage(String content) {
        if (content == null || content.length() <= MAX_AI_MESSAGE_LENGTH) {
            return content;
        }
        return content.substring(0, MAX_AI_MESSAGE_LENGTH) + "\n\n(응답이 길어 일부만 표시했어요.)";
    }

    private String withFeedback(String feedback, String nextQuestion) {
        return feedback + "\n\n" + nextQuestion;
    }

    private AiReviewMessageMode parseMode(String modeValue) {
        if (modeValue == null || modeValue.isBlank()) {
            return AiReviewMessageMode.CHECK_ANSWER;
        }
        try {
            return AiReviewMessageMode.valueOf(modeValue);
        } catch (IllegalArgumentException ex) {
            return AiReviewMessageMode.CHECK_ANSWER;
        }
    }

    private String buildQuestion(Question question, int step) {
        String correct = optionAt(question, question.getCorrectAnswer());
        return switch (step) {
            case 1 -> "이 문제에서 어떤 이유로 그 답을 골랐나요? 본인이 이해한 흐름을 짧게 설명해보세요.";
            case 2 -> "정답은 `" + correct + "`입니다. 이 선택지가 맞는 핵심 이유를 한 문장으로 설명해볼까요?";
            default -> "실무에서 비슷한 상황을 만난다면 어떤 기준으로 판단하면 좋을까요?";
        };
    }

    private String buildFreeQuestionFallback(Question question) {
        String correct = optionAt(question, question.getCorrectAnswer());
        return "좋은 질문이에요. 이 문제에서 핵심은 정답 `" + correct + "`이 왜 맞는지와 내 선택지가 어떤 개념과 헷갈렸는지를 구분하는 거예요.\n\n"
                + "지금은 로컬 AI 응답을 사용할 수 없어 자세한 자유 답변은 제한되지만, 정답 문장을 기준으로 개념 차이를 먼저 정리해보면 좋아요.";
    }

    private AiReviewEvaluation evaluateAnswer(Question question, String answer) {
        String normalized = normalize(answer);
        if (normalized.length() < 8) {
            return AiReviewEvaluation.NEEDS_REVIEW;
        }

        Set<String> keywords = keywords(question);
        long hitCount = keywords.stream()
                .filter(keyword -> normalized.contains(normalize(keyword)))
                .count();

        if (hitCount >= 2 || normalized.length() >= 60) {
            return AiReviewEvaluation.UNDERSTOOD;
        }
        if (hitCount == 1 || normalized.length() >= 25) {
            return AiReviewEvaluation.PARTIAL;
        }
        return AiReviewEvaluation.NEEDS_REVIEW;
    }

    private Set<String> keywords(Question question) {
        Set<String> keywords = new LinkedHashSet<>();
        keywords.addAll(splitKeywords(optionAt(question, question.getCorrectAnswer())));
        keywords.add(inferArea(question));
        keywords.add(question.getTest().getCategory());
        return keywords.stream()
                .filter(keyword -> keyword.length() >= 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<String> splitKeywords(String text) {
        return Arrays.stream(text.split("[^A-Za-z0-9가-힣+.#]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .toList();
    }

    private String feedback(AiReviewEvaluation evaluation) {
        return switch (evaluation) {
            case UNDERSTOOD -> "좋아요. 핵심 흐름을 충분히 잡고 있습니다.";
            case PARTIAL -> "방향은 맞습니다. 다만 핵심 용어와 원인을 조금 더 정확히 연결해보면 좋겠습니다.";
            case NEEDS_REVIEW -> "아직 개념이 흔들리는 상태입니다. 정답이 왜 맞는지 한 단계 더 쪼개서 확인해볼게요.";
        };
    }

    private Optional<TestAnswer> nextWrongAnswer(List<TestAnswer> wrongAnswers, Question currentQuestion) {
        for (int i = 0; i < wrongAnswers.size(); i++) {
            if (Objects.equals(wrongAnswers.get(i).getQuestion().getId(), currentQuestion.getId())
                    && i + 1 < wrongAnswers.size()) {
                return Optional.of(wrongAnswers.get(i + 1));
            }
        }
        return Optional.empty();
    }

    private Optional<TestAnswer> findWrongAnswer(List<TestAnswer> wrongAnswers, Question currentQuestion) {
        return wrongAnswers.stream()
                .filter(answer -> Objects.equals(answer.getQuestion().getId(), currentQuestion.getId()))
                .findFirst();
    }

    private String buildSummary(AiReviewSession session, List<TestAnswer> wrongAnswers) {
        List<AiReviewMessage> userMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .filter(message -> message.getRole() == AiReviewMessageRole.USER)
                .toList();
        long understood = userMessages.stream()
                .filter(message -> message.getEvaluation() == AiReviewEvaluation.UNDERSTOOD)
                .count();
        long needsReview = userMessages.stream()
                .filter(message -> message.getEvaluation() == AiReviewEvaluation.NEEDS_REVIEW)
                .count();

        return "총 " + wrongAnswers.size() + "개의 틀린 문제를 복습했습니다. "
                + "충분히 이해한 답변은 " + understood + "개이고, "
                + "추가 복습이 필요한 답변은 " + needsReview + "개입니다. "
                + "약점 태그: " + weaknessTags(wrongAnswers);
    }

    private String weaknessTags(List<TestAnswer> wrongAnswers) {
        return wrongAnswers.stream()
                .map(answer -> inferArea(answer.getQuestion()))
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private AiReviewSession findOwnedSession(Long userId, Long sessionId) {
        AiReviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new TestNotFoundException("AI 복습 세션을 찾을 수 없습니다."));
        if (!Objects.equals(session.getUser().getId(), userId)) {
            throw new TestNotFoundException("AI 복습 세션을 찾을 수 없습니다.");
        }
        return session;
    }

    private TestResult findOwnedResult(Long userId, Long testResultId) {
        TestResult result = testResultRepository.findById(testResultId)
                .orElseThrow(() -> new TestNotFoundException("테스트 결과를 찾을 수 없습니다."));
        if (!Objects.equals(result.getUser().getId(), userId)) {
            throw new TestNotFoundException("테스트 결과를 찾을 수 없습니다.");
        }
        return result;
    }

    private List<TestAnswer> wrongAnswers(Long testResultId) {
        return testAnswerRepository.findByTestResultId(testResultId).stream()
                .filter(answer -> !Boolean.TRUE.equals(answer.getIsCorrect()))
                .sorted(Comparator.comparing(answer -> answer.getQuestion().getOrderIndex()))
                .toList();
    }

    private AiReviewSessionResponse response(AiReviewSession session) {
        List<TestAnswer> wrongAnswers = wrongAnswers(session.getTestResult().getId());
        return AiReviewSessionResponse.of(
                session,
                wrongAnswers.stream().map(WrongQuestionResponse::from).toList(),
                messages(session)
        );
    }

    private List<AiReviewMessageResponse> messages(AiReviewSession session) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(AiReviewMessageResponse::from)
                .toList();
    }

    private String optionAt(Question question, int index) {
        if (index < 0 || question.getOptions() == null || index >= question.getOptions().size()) {
            return "미응답";
        }
        return question.getOptions().get(index);
    }

    private String inferArea(Question question) {
        String text = normalize(question.getContent() + " " + String.join(" ", question.getOptions()));
        if (text.contains("transaction") || text.contains("트랜잭션")) return "Transaction";
        if (text.contains("jpa") || text.contains("n+1") || text.contains("fetch")) return "JPA";
        if (text.contains("react") || text.contains("state") || text.contains("렌더")) return "React";
        if (text.contains("kafka") || text.contains("offset") || text.contains("partition")) return "Kafka";
        if (text.contains("lock") || text.contains("락")) return "Distributed Lock";
        if (text.contains("docker") || text.contains("ci/cd") || text.contains("aws")) return "DevOps";
        if (text.contains("sql") || text.contains("etl") || text.contains("pipeline")) return "Data";
        if (text.contains("model") || text.contains("모델") || text.contains("mlops")) return "ML";
        if (text.contains("android") || text.contains("ios") || text.contains("flutter") || text.contains("native")) return "Mobile";
        return question.getTest().getCategory();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
