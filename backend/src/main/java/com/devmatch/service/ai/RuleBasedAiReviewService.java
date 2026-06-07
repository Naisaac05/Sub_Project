package com.devmatch.service.ai;

import com.devmatch.dto.aireview.*;
import com.devmatch.config.AiReviewProperties;
import com.devmatch.entity.*;
import com.devmatch.exception.InvalidSessionStateException;
import com.devmatch.exception.TestNotFoundException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RuleBasedAiReviewService {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedAiReviewService.class);
    private static final int MAX_QUESTIONS_PER_WRONG_ANSWER = 3;
    private static final int MAX_AI_MESSAGE_LENGTH = 1800;
    private static final List<AiReviewMessageMode> AI_RESPONSE_LIMIT_MODES = List.of(
            AiReviewMessageMode.CHECK_QUESTION,
            AiReviewMessageMode.EXPLANATION,
            AiReviewMessageMode.NEXT_QUESTION,
            AiReviewMessageMode.FREE_ANSWER
    );

    private final UserRepository userRepository;
    private final TestResultRepository testResultRepository;
    private final TestAnswerRepository testAnswerRepository;
    private final AiReviewSessionRepository sessionRepository;
    private final AiReviewMessageRepository messageRepository;
    private final PythonAiReviewClient pythonAiReviewClient;
    private final OllamaAiReviewClient ollamaAiReviewClient;
    private final AiReviewProperties aiReviewProperties;
    private final SemanticAnswerEvaluator semanticAnswerEvaluator;

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
    public AiReviewSummaryResponse summarizeQuestion(Long userId, Long sessionId, Long questionId) {
        AiReviewSession session = findOwnedSession(userId, sessionId);
        List<TestAnswer> wrongAnswers = wrongAnswers(session.getTestResult().getId());
        TestAnswer wrongAnswer = wrongAnswers.stream()
                .filter(answer -> Objects.equals(answer.getQuestion().getId(), questionId))
                .findFirst()
                .orElseThrow(() -> new TestNotFoundException("요약할 틀린 문제를 찾을 수 없습니다."));
        List<AiReviewMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .filter(message -> message.getQuestion() != null)
                .filter(message -> Objects.equals(message.getQuestion().getId(), questionId))
                .toList();
        Long messageCursor = messageRepository.findTopBySessionIdOrderByIdDesc(sessionId)
                .map(AiReviewMessage::getId)
                .orElse(0L);

        Optional<AiReviewMessage> reusableSummary = reusableSummary(messages, AiReviewMessageMode.QUESTION_SUMMARY);
        if (reusableSummary.isPresent()) {
            return new AiReviewSummaryResponse(
                    questionId,
                    reusableSummary.get().getContent(),
                    false,
                    List.of(AiReviewMessageResponse.from(reusableSummary.get()))
            );
        }

        String summary = buildQuestionStudySummary(wrongAnswer, messages);
        saveAiMessage(session, wrongAnswer.getQuestion(), AiReviewMessageMode.QUESTION_SUMMARY, summary);
        return new AiReviewSummaryResponse(
                questionId,
                summary,
                false,
                messagesAfter(session, messageCursor)
        );
    }

    @Transactional
    public AiReviewSummaryResponse summarizeReview(Long userId, Long sessionId) {
        AiReviewSession session = findOwnedSession(userId, sessionId);
        List<TestAnswer> wrongAnswers = wrongAnswers(session.getTestResult().getId());
        List<AiReviewMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        Long messageCursor = messageRepository.findTopBySessionIdOrderByIdDesc(sessionId)
                .map(AiReviewMessage::getId)
                .orElse(0L);

        Optional<AiReviewMessage> reusableReport = reusableSummary(messages, AiReviewMessageMode.REVIEW_REPORT);
        if (reusableReport.isPresent()) {
            return new AiReviewSummaryResponse(
                    null,
                    reusableReport.get().getContent(),
                    true,
                    List.of(AiReviewMessageResponse.from(reusableReport.get()))
            );
        }

        String summary = buildOverallStudyReport(session, wrongAnswers, messages);
        AiReviewMessage report = messageRepository.save(AiReviewMessage.builder()
                .session(session)
                .question(null)
                .role(AiReviewMessageRole.AI)
                .mode(AiReviewMessageMode.REVIEW_REPORT)
                .content(limitAiMessage(summary))
                .build());

        return new AiReviewSummaryResponse(
                null,
                report.getContent(),
                true,
                messagesAfter(session, messageCursor)
        );
    }

    @Transactional
    public AiReviewSubmitResponse submitAnswer(Long userId, Long sessionId, String answer, String modeValue, Long questionId) {
        AiReviewSession session = findOwnedSession(userId, sessionId);
        if (session.getStatus() == AiReviewStatus.COMPLETED) {
            return new AiReviewSubmitResponse(
                    "COMPLETED",
                    "이미 완료된 복습입니다.",
                    null,
                    true,
                    session.getSummary(),
                    List.of()
            );
        }

        AiReviewMessageMode mode = parseMode(modeValue);
        String normalizedAnswer = answer == null ? "" : answer.trim();

        AiReviewMessage lastAiMessage = latestQuestionMessage(sessionId)
                .orElseThrow(() -> new TestNotFoundException("진행 중인 복습 질문이 없습니다."));
        List<TestAnswer> wrongAnswers = wrongAnswers(session.getTestResult().getId());
        Question currentQuestion = resolveCurrentQuestion(wrongAnswers, lastAiMessage, questionId, mode);
        Long messageCursor = messageRepository.findTopBySessionIdOrderByIdDesc(sessionId)
                .map(AiReviewMessage::getId)
                .orElse(0L);

        if (mode == AiReviewMessageMode.NEXT_QUESTION) {
            return moveToNextQuestion(session, wrongAnswers, currentQuestion, messageCursor);
        }
        if (normalizedAnswer.isBlank()) {
            throw new TestNotFoundException("답변이나 질문을 입력해주세요.");
        }
        if (mode == AiReviewMessageMode.FREE_QUESTION) {
            return answerFreeQuestion(session, wrongAnswers, currentQuestion, normalizedAnswer, messageCursor);
        }

        ensureInitialQuestionMessage(session, currentQuestion);

        AiReviewEvaluation evaluation = evaluateAnswer(currentQuestion, normalizedAnswer);
        messageRepository.save(AiReviewMessage.builder()
                .session(session)
                .question(currentQuestion)
                .role(AiReviewMessageRole.USER)
                .mode(AiReviewMessageMode.CHECK_ANSWER)
                .content(limitAiMessage(normalizedAnswer))
                .evaluation(evaluation)
                .build());

        long aiQuestionCount = countAiResponses(sessionId, currentQuestion.getId());

        String feedback = feedback(evaluation);
        String nextQuestion = null;

        if (evaluation == AiReviewEvaluation.UNDERSTOOD || aiQuestionCount >= MAX_QUESTIONS_PER_WRONG_ANSWER) {
            Optional<TestAnswer> nextWrongAnswer = nextWrongAnswer(wrongAnswers, currentQuestion);
            if (nextWrongAnswer.isPresent()) {
                Question question = nextWrongAnswer.get().getQuestion();
                nextQuestion = generateFirstQuestion(
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
            nextQuestion = generateFollowUp(
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
                messagesAfter(session, messageCursor)
        );
    }

    private AiReviewSubmitResponse answerFreeQuestion(
            AiReviewSession session,
            List<TestAnswer> wrongAnswers,
            Question currentQuestion,
            String questionText,
            Long messageCursor
    ) {
        long freeQuestionCount = countFreeQuestions(session.getId(), currentQuestion.getId());
        if (freeQuestionCount >= MAX_QUESTIONS_PER_WRONG_ANSWER) {
            throw new InvalidSessionStateException("이 문제는 AI 질문/답변을 3개까지 사용할 수 있습니다. 다음 문제로 넘어가세요.");
        }

        messageRepository.save(AiReviewMessage.builder()
                .session(session)
                .question(currentQuestion)
                .role(AiReviewMessageRole.USER)
                .mode(AiReviewMessageMode.FREE_QUESTION)
                .content(limitAiMessage(questionText))
                .build());

        Optional<TestAnswer> currentWrongAnswer = findWrongAnswer(wrongAnswers, currentQuestion);
        String selectedAnswer = currentWrongAnswer
                .map(testAnswer -> optionAt(currentQuestion, testAnswer.getSelectedAnswer()))
                .orElse("");
        AiGeneratedAnswer generatedAnswer = generateFreeQuestionAnswer(
                currentQuestion,
                optionAt(currentQuestion, currentQuestion.getCorrectAnswer()),
                selectedAnswer,
                questionText
        ).orElseGet(() -> AiGeneratedAnswer.plain(buildFreeQuestionFallback(currentQuestion, questionText)));

        String followUpQuestion = AiReviewFollowUpSupport.buildFreeQuestionFollowUp(currentQuestion, questionText, generatedAnswer);
        AiGeneratedAnswer answerWithFollowUp = withAnswerContent(
                generatedAnswer,
                AiReviewFollowUpSupport.appendFollowUp(generatedAnswer.answer(), followUpQuestion)
        );
        saveAiMessage(session, currentQuestion, AiReviewMessageMode.FREE_ANSWER, answerWithFollowUp);

        return new AiReviewSubmitResponse(
                "FREE_QUESTION",
                answerWithFollowUp.answer(),
                followUpQuestion,
                false,
                session.getSummary(),
                messagesAfter(session, messageCursor)
        );
    }

    private AiReviewSubmitResponse moveToNextQuestion(
            AiReviewSession session,
            List<TestAnswer> wrongAnswers,
            Question currentQuestion,
            Long messageCursor
    ) {
        Optional<TestAnswer> nextWrongAnswer = nextWrongAnswer(wrongAnswers, currentQuestion);
        if (nextWrongAnswer.isPresent()) {
            TestAnswer nextAnswer = nextWrongAnswer.get();
            Question question = nextAnswer.getQuestion();
            String nextQuestion = buildQuestion(question, 1);
            saveAiMessage(session, question, AiReviewMessageMode.NEXT_QUESTION, nextQuestion);

            return new AiReviewSubmitResponse(
                    "NEXT_QUESTION",
                    "다음 틀린 문제로 이동했어요.",
                    nextQuestion,
                    false,
                    session.getSummary(),
                    messagesAfter(session, messageCursor)
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
                messagesAfter(session, messageCursor)
        );
    }

    private void askFirstQuestion(AiReviewSession session, List<TestAnswer> wrongAnswers) {
        if (!wrongAnswers.isEmpty()) {
            TestAnswer firstAnswer = wrongAnswers.get(0);
            Question firstQuestion = firstAnswer.getQuestion();
            String question = generateFirstQuestion(
                    firstQuestion,
                    optionAt(firstQuestion, firstQuestion.getCorrectAnswer()),
                    optionAt(firstQuestion, firstAnswer.getSelectedAnswer())
            ).orElse(buildQuestion(firstQuestion, 1));
            saveAiMessage(session, firstQuestion, AiReviewMessageMode.CHECK_QUESTION, question);
        }
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

    private void saveAiMessage(AiReviewSession session, Question question, AiReviewMessageMode mode, AiGeneratedAnswer answer) {
        messageRepository.save(AiReviewMessage.builder()
                .session(session)
                .question(question)
                .role(AiReviewMessageRole.AI)
                .mode(mode)
                .content(limitAiMessage(answer.answer()))
                .aiRoute(answer.route())
                .aiResolvedQuery(answer.resolvedQuery())
                .aiCorrectionType(answer.correctionType())
                .aiMatchedConceptId(answer.matchedConceptId())
                .aiAnswerStyle(answer.answerStyle())
                .aiQualityFlags(answer.qualityFlags() == null ? "" : String.join(",", answer.qualityFlags()))
                .aiCandidateId(answer.candidateId())
                .aiLatencyMs(answer.latencyMs())
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

    private Optional<String> generateFirstQuestion(Question question, String correctAnswer, String selectedAnswer) {
        try {
            Optional<String> pythonAnswer = pythonAiReviewClient.generateFirstQuestion(
                    question,
                    correctAnswer,
                    selectedAnswer
            ).map(AiGeneratedAnswer::answer);
            if (pythonAnswer.isPresent()) {
                return pythonAnswer;
            }
            return ollamaAiReviewClient.generateFirstQuestion(question, correctAnswer, selectedAnswer);
        } catch (RuntimeException ex) {
            log.warn("AI first question generation failed. questionId={}, message={}", question.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> generateFollowUp(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String userAnswer,
            AiReviewEvaluation evaluation,
            int nextStep
    ) {
        try {
            Optional<String> pythonAnswer = pythonAiReviewClient.generateFollowUp(
                    question,
                    correctAnswer,
                    selectedAnswer,
                    userAnswer,
                    evaluation,
                    nextStep
            ).map(AiGeneratedAnswer::answer);
            if (pythonAnswer.isPresent()) {
                return pythonAnswer;
            }
            return ollamaAiReviewClient.generateFollowUp(
                    question,
                    correctAnswer,
                    selectedAnswer,
                    userAnswer,
                    evaluation,
                    nextStep
            );
        } catch (RuntimeException ex) {
            log.warn("AI follow-up generation failed. questionId={}, message={}", question.getId(), ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AiGeneratedAnswer> generateFreeQuestionAnswer(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String userQuestion
    ) {
        try {
            Optional<AiGeneratedAnswer> pythonAnswer = pythonAiReviewClient.answerFreeQuestion(
                    question,
                    correctAnswer,
                    selectedAnswer,
                    userQuestion
            );
            if (pythonAnswer.isPresent()) {
                return pythonAnswer;
            }
            return ollamaAiReviewClient.answerFreeQuestion(question, correctAnswer, selectedAnswer, userQuestion)
                    .map(AiGeneratedAnswer::plain);
        } catch (RuntimeException ex) {
            log.warn("AI free-question generation failed. questionId={}, message={}", question.getId(), ex.getMessage());
            return Optional.empty();
        }
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
            case 1 -> "이 문제의 핵심 개념이 무엇인지 먼저 짚어볼게요. 내 선택지를 고른 이유와 정답이 되는 조건을 한 문장으로 설명해볼까요?";
            case 2 -> "정답은 `" + correct + "`입니다. 내 선택지가 왜 정답이 아닌지 핵심 차이를 한 문장으로 다시 정리해볼까요?";
            default -> "비슷한 문제를 다시 만나면 어떤 기준으로 판단하면 좋을까요? 판단 기준을 짧게 적어보세요.";
        };
    }

    private String buildFreeQuestionFallback(Question question, String userQuestion) {
        Optional<String> topicFallback = topicSpecificFallback(question, userQuestion);
        if (topicFallback.isPresent()) {
            return topicFallback.get();
        }
        String correct = optionAt(question, question.getCorrectAnswer());
        return "좋은 질문이에요. 지금은 로컬 AI 응답이 느리거나 실패해서 자세한 답변 대신 핵심 기준만 먼저 정리할게요.\n\n"
                + "이 문제에서는 정답 `" + correct + "`이 왜 맞는지와 내 선택지가 어떤 개념을 놓쳤는지를 구분해보면 좋습니다.";
    }

    private AiGeneratedAnswer withAnswerContent(AiGeneratedAnswer source, String content) {
        return new AiGeneratedAnswer(
                content,
                source.route(),
                source.resolvedQuery(),
                source.correctionType(),
                source.matchedConceptId(),
                source.answerStyle(),
                source.qualityFlags(),
                source.candidateId(),
                source.latencyMs(),
                source.observabilityEvents()
        );
    }

    private Optional<String> topicSpecificFallback(Question question, String userQuestion) {
        String topicText = String.join(" ",
                userQuestion == null ? "" : userQuestion,
                question == null ? "" : question.getContent(),
                question == null || question.getOptions() == null ? "" : String.join(" ", question.getOptions())
        );
        String normalized = normalize(topicText);
        String compact = normalized.replace(" ", "");
        if (isLayerQuestion(normalized)) {
            return Optional.of("계층은 보통 `Controller -> Service -> Repository -> Entity`로 나눠서 봅니다. "
                    + "Controller는 HTTP 요청과 응답을 받고, Service는 비즈니스 규칙과 transaction boundary를 잡고, Repository는 DB 접근을 맡고, Entity는 DB에 저장되는 도메인 상태를 표현합니다. "
                    + "그래서 여러 DB 작업을 하나의 업무 흐름으로 묶어야 하면 Repository 하나가 아니라 Service 계층에서 transaction을 관리하는 것이 정답이 됩니다.");
        }
        if (compact.contains("gittag") || (normalized.contains("git") && normalized.contains("tag"))) {
            return Optional.of("Git tag는 특정 commit에 붙이는 고정 이름표입니다. 보통 `v1.0.0`처럼 release version을 표시할 때 사용합니다. branch는 계속 움직일 수 있지만 tag는 한 commit을 가리키므로 배포 버전 추적, rollback 기준, 릴리스 노트 연결에 유용합니다.");
        }
        if (normalized.contains("idempotent") || normalized.contains("idempotency")) {
            return Optional.of("Idempotent 설계는 같은 요청이나 메시지가 여러 번 들어와도 최종 결과가 한 번 처리한 것과 같게 유지하는 방식입니다. 네트워크 재시도, 중복 클릭, 메시지 재전달 상황에서 데이터가 두 번 생성되거나 금액이 두 번 차감되는 문제를 막기 위해 idempotency key, unique constraint, 처리 이력 테이블을 사용합니다.");
        }
        if (normalized.contains("network") || normalized.contains("네트워크")
                || (normalized.contains("자동") && normalized.contains("연결"))
                || compact.contains("autoconnect")) {
            return Optional.of("network 다이어그램에서 auto layout이나 auto connect 기능은 도구에 따라 가능합니다. 다만 도구가 의미를 완전히 추론해 없는 관계를 자동 생성한다기보다, 사용자가 node와 edge 데이터를 주면 layout 엔진이 보기 좋게 배치하고 연결선을 정리하는 방식입니다. Mermaid, Graphviz, Cytoscape 같은 도구가 이런 자동 배치를 도와줍니다.");
        }
        return Optional.empty();
    }

    private boolean isLayerQuestion(String normalized) {
        return normalized.contains("계층")
                || normalized.contains("layer")
                || normalized.contains("controller")
                || normalized.contains("service")
                || normalized.contains("repository")
                || normalized.contains("entity");
    }

    private AiReviewEvaluation evaluateAnswer(Question question, String answer) {
        if (isSemanticEvaluationEnabled() && semanticAnswerEvaluator != null) {
            Optional<AiReviewEvaluation> semanticEvaluation = semanticAnswerEvaluator.evaluate(question, answer);
            if (semanticEvaluation.isPresent()) {
                return semanticEvaluation.get();
            }
        }

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

    private boolean isSemanticEvaluationEnabled() {
        return aiReviewProperties != null
                && aiReviewProperties.evaluation() != null
                && aiReviewProperties.evaluation().semanticEnabled();
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

    private Question resolveCurrentQuestion(
            List<TestAnswer> wrongAnswers,
            AiReviewMessage lastAiMessage,
            Long questionId,
            AiReviewMessageMode mode
    ) {
        if (questionId != null && mode != AiReviewMessageMode.NEXT_QUESTION) {
            return AiReviewContextSupport.questionById(wrongAnswers, questionId);
        }
        return lastAiMessage.getQuestion();
    }

    private Optional<AiReviewMessage> latestQuestionMessage(Long sessionId) {
        return AiReviewContextSupport.latestQuestionMessage(
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
        );
    }

    private void ensureInitialQuestionMessage(AiReviewSession session, Question question) {
        long questionCount = messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                session.getId(),
                question.getId(),
                AiReviewMessageRole.AI,
                List.of(AiReviewMessageMode.CHECK_QUESTION, AiReviewMessageMode.NEXT_QUESTION)
        );
        if (questionCount == 0) {
            saveAiMessage(session, question, AiReviewMessageMode.CHECK_QUESTION, buildQuestion(question, 1));
        }
    }

    private long countAiResponses(Long sessionId, Long questionId) {
        return messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                sessionId,
                questionId,
                AiReviewMessageRole.AI,
                AI_RESPONSE_LIMIT_MODES
        );
    }

    private long countFreeQuestions(Long sessionId, Long questionId) {
        return messageRepository.countBySessionIdAndQuestionIdAndRoleAndModeIn(
                sessionId,
                questionId,
                AiReviewMessageRole.USER,
                List.of(AiReviewMessageMode.FREE_QUESTION)
        );
    }

    private Optional<AiReviewMessage> reusableSummary(List<AiReviewMessage> messages, AiReviewMessageMode summaryMode) {
        Optional<AiReviewMessage> latestSummary = messages.stream()
                .filter(message -> message.getMode() == summaryMode)
                .reduce((first, second) -> second);
        if (latestSummary.isEmpty()) {
            return Optional.empty();
        }
        long latestStudyMessageId = messages.stream()
                .filter(message -> message.getMode() != AiReviewMessageMode.QUESTION_SUMMARY)
                .filter(message -> message.getMode() != AiReviewMessageMode.REVIEW_REPORT)
                .mapToLong(AiReviewMessage::getId)
                .max()
                .orElse(0L);
        return latestSummary.get().getId() >= latestStudyMessageId ? latestSummary : Optional.empty();
    }

    private String buildQuestionStudySummary(TestAnswer wrongAnswer, List<AiReviewMessage> messages) {
        Question question = wrongAnswer.getQuestion();
        List<AiReviewMessage> userMessages = messages.stream()
                .filter(message -> message.getRole() == AiReviewMessageRole.USER)
                .toList();
        String latestEvaluation = userMessages.stream()
                .map(AiReviewMessage::getEvaluation)
                .filter(Objects::nonNull)
                .reduce((first, second) -> second)
                .map(this::evaluationStudyLabel)
                .orElse("아직 평가 전");
        String lastUserAnswer = userMessages.stream()
                .reduce((first, second) -> second)
                .map(AiReviewMessage::getContent)
                .map(this::shorten)
                .orElse("아직 직접 답변이 없습니다.");
        long freeQuestionCount = userMessages.stream()
                .filter(message -> message.getMode() == AiReviewMessageMode.FREE_QUESTION)
                .count();
        String selectedAnswer = optionAt(question, wrongAnswer.getSelectedAnswer());
        String correctAnswer = optionAt(question, question.getCorrectAnswer());

        return new StudySummary(
                conceptNoteTitle(question),
                conceptCore(question),
                answerCriterion(question, correctAnswer),
                structureConnection(question),
                selectedAnswer,
                confusingPoint(question, selectedAnswer),
                trapReason(question, selectedAnswer),
                practicalConnection(question),
                counterExample(question, correctAnswer),
                wrongReason(question, selectedAnswer),
                recallCondition(question),
                latestEvaluation,
                lastUserAnswer,
                freeQuestionCount
        ).toMarkdown();
    }

    private String conceptNoteTitle(Question question) {
        if (isTransactionalQuestion(question)) {
            return "@Transactional 위치";
        }
        return inferArea(question) + " 개념 충돌 기록";
    }

    private String conceptCore(Question question) {
        if (isTransactionalQuestion(question)) {
            return "트랜잭션 경계를 어느 계층에서 관리하는가?";
        }
        return shorten(question.getContent());
    }

    private String wrongReason(Question question, String selectedAnswer) {
        if (isTransactionalQuestion(question)) {
            return "DB 접근과 트랜잭션 책임을 혼동해서 " + selectedAnswer + "를 선택함.";
        }
        return "문제의 키워드와 정답 책임을 혼동해서 " + selectedAnswer + "를 선택함.";
    }

    private String answerCriterion(Question question, String correctAnswer) {
        if (isTransactionalQuestion(question)) {
            return "트랜잭션은 비즈니스 로직 단위로 관리해야 하므로 " + correctAnswer + " 계층에 적용한다.";
        }
        return "정답은 키워드가 아니라 책임과 동작 조건으로 판단한다. 이 문제의 정답 기준은 " + correctAnswer + "이다.";
    }

    private String structureConnection(Question question) {
        if (isTransactionalQuestion(question)) {
            return """
                    Controller -> 요청 처리
                    Service -> 비즈니스 로직, 트랜잭션
                    Repository -> DB 접근
                    """.strip();
        }
        return "문제의 개념을 역할, 책임, 실행 흐름 순서로 다시 연결한다.";
    }

    private String confusingPoint(Question question, String selectedAnswer) {
        if (isTransactionalQuestion(question)) {
            return selectedAnswer + "도 DB를 다루지만 트랜잭션의 책임 계층은 아님.";
        }
        return selectedAnswer + "가 그럴듯해 보이는 이유와 실제 정답 조건을 분리해서 본다.";
    }

    private String trapReason(Question question, String selectedAnswer) {
        if (isTransactionalQuestion(question)) {
            return """
                    - "DB" 키워드 때문에 Repository로 유도
                    - "interface 선언부" 같은 표현으로 세부 구현처럼 보이게 함
                    - DB 접근 계층과 업무 트랜잭션 계층을 같은 책임처럼 착각하게 함
                    """.strip();
        }
        return "- 보기의 키워드가 정답처럼 보이게 유도\n- 세부 구현 표현으로 핵심 책임 판단을 흐리게 함";
    }

    private String practicalConnection(Question question) {
        if (isTransactionalQuestion(question)) {
            return "회원가입 시 회원 저장 + 포인트 저장을 하나의 트랜잭션으로 묶기 위해 Service에 적용한다.";
        }
        return "실무에서는 이 개념이 어떤 책임을 어느 계층에 둘지 결정할 때 다시 등장한다.";
    }

    private String recallCondition(Question question) {
        if (isTransactionalQuestion(question)) {
            return "트랜잭션은 업무 단위니까 Service";
        }
        return "정답 조건을 먼저 말하고 보기를 확인한다";
    }

    private String counterExample(Question question, String correctAnswer) {
        if (isTransactionalQuestion(question)) {
            return "Repository에서 직접 @Transactional을 붙이면 단건 DB 연산만 감싸고, 여러 작업을 하나의 업무 단위로 묶지 못합니다.";
        }
        return "정답이 " + correctAnswer + "인 이유를 반대로 물어보면: 이 조건이 없으면 어떤 문제가 생길까?";
    }

    private record StudySummary(
            String title,
            String coreConcept,
            String criterion,
            String structureConnection,
            String selectedAnswer,
            String confusingPoint,
            String trapReason,
            String practicalConnection,
            String counterExample,
            String wrongCause,
            String oneLiner,
            String currentEvaluation,
            String lastExplanation,
            long freeQuestionCount
    ) {
        private String toMarkdown() {
            return """
                    # %s

                    ## 핵심 개념
                    - **문제의 중심:** %s
                    - **구조 연결:** %s

                    ## 판단 기준
                    - **정답 조건**: %s
                    - **판단 순서:** 문제의 키워드보다 “어느 책임을 어느 계층이 가져야 하는가”를 먼저 본다.

                    ## 오답 함정
                    - **내가 고른 보기:** %s
                    - **헷갈린 지점:** %s
                    - **함정 이유:** %s

                    ## 실무 연결
                    - %s

                    ## 반례 / 예외
                    - %s

                    ## 내 오답 원인
                    - **현재 이해 상태:** %s
                    - **마지막 내 답변:** %s
                    - **추가 질문 횟수:** %d회
                    - **원인:** %s

                    ## 한 줄 압축
                    - **%s**
                    """.formatted(
                    title,
                    coreConcept,
                    structureConnection,
                    criterion,
                    selectedAnswer,
                    confusingPoint,
                    trapReason,
                    practicalConnection,
                    counterExample,
                    currentEvaluation,
                    lastExplanation,
                    freeQuestionCount,
                    wrongCause,
                    oneLiner
            );
        }
    }

    private boolean isTransactionalQuestion(Question question) {
        String text = normalize(question.getContent() + " " + String.join(" ", question.getOptions()));
        return text.contains("@transactional") || text.contains("transactional") || text.contains("트랜잭션");
    }

    private String buildOverallStudyReport(
            AiReviewSession session,
            List<TestAnswer> wrongAnswers,
            List<AiReviewMessage> messages
    ) {
        Map<Long, List<AiReviewMessage>> messagesByQuestion = messages.stream()
                .filter(message -> message.getQuestion() != null)
                .collect(Collectors.groupingBy(
                        message -> message.getQuestion().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        String weakAreas = weaknessTags(wrongAnswers);
        long understood = messages.stream()
                .filter(message -> message.getEvaluation() == AiReviewEvaluation.UNDERSTOOD)
                .count();
        long needsReview = messages.stream()
                .filter(message -> message.getEvaluation() == AiReviewEvaluation.NEEDS_REVIEW)
                .count();
        long freeQuestions = messages.stream()
                .filter(message -> message.getMode() == AiReviewMessageMode.FREE_QUESTION)
                .count();

        List<QuestionReview> reviews = wrongAnswers.stream()
                .map(answer -> {
                    Question question = answer.getQuestion();
                    List<AiReviewMessage> questionMessages = messagesByQuestion.getOrDefault(
                            question.getId(),
                            List.of()
                    );
                    String evaluation = questionMessages.stream()
                            .map(AiReviewMessage::getEvaluation)
                            .filter(Objects::nonNull)
                            .reduce((first, second) -> second)
                            .map(this::evaluationStudyLabel)
                            .orElse("아직 복습 전");
                    return new QuestionReview(
                            inferArea(question),
                            shorten(question.getContent()),
                            safeOptionAt(question, answer.getSelectedAnswer()),
                            safeOptionAt(question, question.getCorrectAnswer()),
                            evaluation,
                            classifyWeaknesses(answer, questionMessages)
                    );
                })
                .toList();

        return new OverallStudyReport(
                wrongAnswers.size(),
                weakAreas.isBlank() ? safeText(session.getCourseKey(), "미분류") : weakAreas,
                understood,
                needsReview,
                freeQuestions,
                reviews,
                topWeaknesses(reviews)
        ).toMarkdown();
    }

    private List<String> classifyWeaknesses(TestAnswer answer, List<AiReviewMessage> messages) {
        Question question = answer.getQuestion();
        String selected = safeOptionAt(question, answer.getSelectedAnswer());
        String correct = safeOptionAt(question, question.getCorrectAnswer());
        String text = normalize(String.join(" ",
                question.getContent(),
                String.join(" ", question.getOptions() == null ? List.of() : question.getOptions()),
                selected,
                correct,
                messages.stream().map(AiReviewMessage::getContent).filter(Objects::nonNull).collect(Collectors.joining(" "))
        ));
        LinkedHashSet<String> weaknesses = new LinkedHashSet<>();
        if (text.contains("api") || text.contains("dto") || text.contains("entity")
                || text.contains("비슷") || text.contains("키워드")) {
            weaknesses.add("키워드 유사성 함정");
        }
        if (text.contains("service") || text.contains("repository") || text.contains("controller")
                || text.contains("entity") || text.contains("transactional") || text.contains("계층")
                || text.contains("책임")) {
            weaknesses.add("책임/계층 분리 부족");
        }
        if (text.contains("조건") || text.contains("기준") || text.contains("언제")
                || text.contains("어느") || text.contains("정답")) {
            weaknesses.add("조건 기반 판단 부족");
        }
        if (text.contains("예외") || text.contains("반례") || text.contains("exception")
                || text.contains("error")) {
            weaknesses.add("예외/반례 이해 부족");
        }
        if (weaknesses.isEmpty()) {
            weaknesses.add("조건 기반 판단 부족");
        }
        if (weaknesses.size() == 1) {
            weaknesses.add("키워드 유사성 함정");
        }
        return weaknesses.stream().limit(3).toList();
    }

    private List<WeaknessCount> topWeaknesses(List<QuestionReview> reviews) {
        Map<String, Long> counts = reviews.stream()
                .flatMap(review -> review.weaknesses().stream())
                .collect(Collectors.groupingBy(
                        weakness -> weakness,
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
        for (String weakness : List.of("키워드 유사성 함정", "책임/계층 분리 부족", "조건 기반 판단 부족", "예외/반례 이해 부족")) {
            counts.putIfAbsent(weakness, 0L);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(entry -> new WeaknessCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private String safeOptionAt(Question question, Integer index) {
        try {
            return safeText(optionAt(question, index), "선택지 정보 없음");
        } catch (RuntimeException ex) {
            return "선택지 정보 없음";
        }
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record QuestionReview(
            String area,
            String question,
            String selectedAnswer,
            String correctAnswer,
            String evaluation,
            List<String> weaknesses
    ) {
        private String priorityLine(int index) {
            return "- **%d순위** `%s` %s\n  - 선택: %s\n  - 정답: %s\n  - 상태: %s\n  - 약점: %s".formatted(
                    index,
                    area,
                    question,
                    selectedAnswer,
                    correctAnswer,
                    evaluation,
                    String.join(", ", weaknesses)
            );
        }
    }

    private record WeaknessCount(String name, long count) {
        private String toMarkdown(int index) {
            String evidence = count > 0 ? count + "개 문제에서 감지" : "직접 감지는 약하지만 기본 점검 필요";
            return "%d. **%s** - %s".formatted(index, name, evidence);
        }
    }

    private record OverallStudyReport(
            int wrongAnswerCount,
            String weakAreas,
            long understood,
            long needsReview,
            long freeQuestions,
            List<QuestionReview> reviews,
            List<WeaknessCount> topWeaknesses
    ) {
        private String toMarkdown() {
            String scope = reviews.isEmpty()
                    ? "- 오늘 기록된 오답 문제가 없습니다."
                    : reviews.stream()
                    .map(review -> "- `%s` %s".formatted(review.area(), review.question()))
                    .collect(Collectors.joining("\n"));
            String top = topWeaknesses.isEmpty()
                    ? "1. **조건 기반 판단 부족** - 복습 데이터가 부족해 기본 점검 항목으로 표시"
                    : java.util.stream.IntStream.range(0, topWeaknesses.size())
                    .mapToObj(index -> topWeaknesses.get(index).toMarkdown(index + 1))
                    .collect(Collectors.joining("\n"));
            String repeated = reviews.isEmpty()
                    ? "- 아직 반복 패턴을 판단할 데이터가 부족합니다."
                    : reviews.stream()
                    .flatMap(review -> review.weaknesses().stream())
                    .distinct()
                    .map(weakness -> "- **%s:** 문제 문장 키워드만 보지 말고 정답이 되는 조건을 먼저 말해본다.".formatted(weakness))
                    .collect(Collectors.joining("\n"));
            String priorities = reviews.isEmpty()
                    ? "- 우선 복습할 문제가 없습니다."
                    : java.util.stream.IntStream.range(0, Math.min(3, reviews.size()))
                    .mapToObj(index -> reviews.get(index).priorityLine(index + 1))
                    .collect(Collectors.joining("\n"));

            return """
                    # 전체 복습 리포트

                    ## 오늘의 오답 범위
                    - **오답 수:** %d개
                    - **주요 영역:** %s
                    - **이해 완료 답변:** %d개
                    - **추가 복습 필요 답변:** %d개
                    - **자유 질문:** %d개

                    %s

                    ## 핵심 약점 TOP 3
                    %s

                    ## 반복 오답 패턴
                    %s

                    ## 우선 복습할 문제
                    %s

                    ## 다음 복습 전략
                    - 먼저 각 문제의 **정답 조건**을 한 문장으로 말한 뒤 선택지를 다시 본다.
                    - `키워드 유사성 함정`은 보기의 단어가 익숙한지보다 역할과 책임이 맞는지로 판별한다.
                    - `책임/계층 분리 부족`은 Controller, Service, Repository, Entity의 책임을 표로 다시 나눠본다.
                    - `조건 기반 판단 부족`은 “항상 맞는가, 특정 조건에서만 맞는가”를 체크한다.
                    - `예외/반례 이해 부족`은 정답이 아닌 상황을 하나씩 만들어 본다.

                    ## 오늘의 한 줄 피드백
                    - **오늘의 핵심은 정답 보기 암기가 아니라, 정답이 되는 조건과 반례를 먼저 말하는 연습입니다.**
                    """.formatted(
                    wrongAnswerCount,
                    weakAreas,
                    understood,
                    needsReview,
                    freeQuestions,
                    scope,
                    top,
                    repeated,
                    priorities
            );
        }
    }

    private String evaluationStudyLabel(AiReviewEvaluation evaluation) {
        return switch (evaluation) {
            case UNDERSTOOD -> "이해 완료";
            case PARTIAL -> "부분 이해";
            case NEEDS_REVIEW -> "추가 복습 필요";
        };
    }

    private String shorten(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= 140 ? compact : compact.substring(0, 140) + "...";
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
        return AiReviewContextSupport.requireOwnedSession(sessionRepository.findById(sessionId), userId);
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
        return AiReviewContextSupport.wrongAnswers(testAnswerRepository.findByTestResultId(testResultId));
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

    private List<AiReviewMessageResponse> messagesAfter(AiReviewSession session, Long messageCursor) {
        long cursor = messageCursor == null ? 0L : messageCursor;
        return messageRepository.findBySessionIdAndIdGreaterThanOrderByCreatedAtAsc(session.getId(), cursor).stream()
                .map(AiReviewMessageResponse::from)
                .toList();
    }

    private String optionAt(Question question, int index) {
        return AiReviewContextSupport.optionAt(question, index);
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
