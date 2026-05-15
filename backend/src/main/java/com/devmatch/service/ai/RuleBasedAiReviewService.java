package com.devmatch.service.ai;

import com.devmatch.dto.aireview.*;
import com.devmatch.entity.*;
import com.devmatch.exception.InvalidSessionStateException;
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
                .content(normalizedAnswer)
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
                .content(questionText)
                .build());

        Optional<TestAnswer> currentWrongAnswer = findWrongAnswer(wrongAnswers, currentQuestion);
        String selectedAnswer = currentWrongAnswer
                .map(testAnswer -> optionAt(currentQuestion, testAnswer.getSelectedAnswer()))
                .orElse("");
        String answer = answerFreeQuestion(
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
            String nextQuestion = generateFirstQuestion(
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
        return Optional.of(buildQuestion(question, 1));
    }

    private Optional<String> generateFollowUp(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String userAnswer,
            AiReviewEvaluation evaluation,
            int nextStep
    ) {
        Optional<String> pythonAnswer = pythonAiReviewClient.generateFollowUp(
                question,
                correctAnswer,
                selectedAnswer,
                userAnswer,
                evaluation,
                nextStep
        );
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
    }

    private Optional<String> answerFreeQuestion(
            Question question,
            String correctAnswer,
            String selectedAnswer,
            String userQuestion
    ) {
        Optional<String> pythonAnswer = pythonAiReviewClient.answerFreeQuestion(
                question,
                correctAnswer,
                selectedAnswer,
                userQuestion
        );
        if (pythonAnswer.isPresent()) {
            return pythonAnswer;
        }
        return ollamaAiReviewClient.answerFreeQuestion(question, correctAnswer, selectedAnswer, userQuestion);
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

    private String buildFreeQuestionFallback(Question question) {
        String correct = optionAt(question, question.getCorrectAnswer());
        return "좋은 질문이에요. 지금은 로컬 AI 응답이 느리거나 실패해서 자세한 답변 대신 핵심 기준만 먼저 정리할게요.\n\n"
                + "이 문제에서는 정답 `" + correct + "`이 왜 맞는지와 내 선택지가 어떤 개념을 놓쳤는지를 구분해보면 좋습니다.";
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

    private Question resolveCurrentQuestion(
            List<TestAnswer> wrongAnswers,
            AiReviewMessage lastAiMessage,
            Long questionId,
            AiReviewMessageMode mode
    ) {
        if (questionId != null && mode != AiReviewMessageMode.NEXT_QUESTION) {
            return wrongAnswers.stream()
                    .map(TestAnswer::getQuestion)
                    .filter(question -> Objects.equals(question.getId(), questionId))
                    .findFirst()
                    .orElseThrow(() -> new TestNotFoundException("선택한 복습 문제를 찾을 수 없습니다."));
        }
        return lastAiMessage.getQuestion();
    }

    private Optional<AiReviewMessage> latestQuestionMessage(Long sessionId) {
        List<AiReviewMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        ListIterator<AiReviewMessage> iterator = messages.listIterator(messages.size());
        while (iterator.hasPrevious()) {
            AiReviewMessage message = iterator.previous();
            if (message.getRole() != AiReviewMessageRole.AI || message.getQuestion() == null) {
                continue;
            }
            if (message.getMode() == AiReviewMessageMode.QUESTION_SUMMARY
                    || message.getMode() == AiReviewMessageMode.REVIEW_REPORT) {
                continue;
            }
            return Optional.of(message);
        }
        return Optional.empty();
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
        List<AiReviewMessage> aiMessages = messages.stream()
                .filter(message -> message.getRole() == AiReviewMessageRole.AI)
                .filter(message -> message.getMode() != AiReviewMessageMode.QUESTION_SUMMARY)
                .filter(message -> message.getMode() != AiReviewMessageMode.REVIEW_REPORT)
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
        String keyAiQuestion = aiMessages.stream()
                .findFirst()
                .map(AiReviewMessage::getContent)
                .map(this::shorten)
                .orElse("AI 꼬리질문이 아직 없습니다.");
        long freeQuestionCount = userMessages.stream()
                .filter(message -> message.getMode() == AiReviewMessageMode.FREE_QUESTION)
                .count();

        return """
                문제별 복습 요약

                1. 원래 문제
                %s

                2. 오답 흐름
                - 내 선택: %s
                - 정답: %s
                - 현재 이해도: %s

                3. 핵심 꼬리질문
                %s

                4. 내가 마지막으로 설명한 내용
                %s

                5. 다시 풀 때 체크할 포인트
                - 보기의 표현을 외우기보다 정답이 되는 조건을 먼저 말해보기
                - 내 선택이 왜 틀렸는지 한 문장으로 반박해보기
                - 비슷한 문제가 나오면 키워드보다 동작 원리와 예외 상황을 같이 확인하기

                6. 추가 질문 기록
                자유 질문 %d개
                """.formatted(
                question.getContent(),
                optionAt(question, wrongAnswer.getSelectedAnswer()),
                optionAt(question, question.getCorrectAnswer()),
                latestEvaluation,
                keyAiQuestion,
                lastUserAnswer,
                freeQuestionCount
        );
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

        String questionLines = wrongAnswers.stream()
                .map(answer -> {
                    List<AiReviewMessage> questionMessages = messagesByQuestion.getOrDefault(
                            answer.getQuestion().getId(),
                            List.of()
                    );
                    String evaluation = questionMessages.stream()
                            .map(AiReviewMessage::getEvaluation)
                            .filter(Objects::nonNull)
                            .reduce((first, second) -> second)
                            .map(this::evaluationStudyLabel)
                            .orElse("점검 전");
                    return "- " + inferArea(answer.getQuestion()) + ": "
                            + shorten(answer.getQuestion().getContent())
                            + " / " + evaluation;
                })
                .collect(Collectors.joining("\n"));

        return """
                전체 복습 리포트

                1. 오늘의 오답 범위
                - 틀린 문제: %d개
                - 주요 약점: %s
                - 이해 완료 답변: %d개
                - 추가 복습 필요 답변: %d개
                - 자유 질문: %d개

                2. 문제별 상태
                %s

                3. 공통 약점
                - 정답 키워드를 알고 있어도 왜 그 보기가 정답인지 설명하는 힘을 더 키워야 합니다.
                - 틀린 선택지를 반박하는 연습이 필요합니다.
                - 문제를 다시 풀 때는 개념 정의, 적용 조건, 예외 상황 순서로 점검하면 좋습니다.

                4. 다음 복습 순서
                - 먼저 '추가 복습 필요'가 남은 문제를 다시 클릭해서 질문하기
                - 각 문제 요약을 읽고 30초 안에 정답 이유를 말해보기
                - 마지막에 같은 유형 문제를 한 번 더 풀어보기
                """.formatted(
                wrongAnswers.size(),
                weakAreas.isBlank() ? session.getCourseKey() : weakAreas,
                understood,
                needsReview,
                freeQuestions,
                questionLines.isBlank() ? "- 아직 정리할 대화가 없습니다." : questionLines
        );
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

    private List<AiReviewMessageResponse> messagesAfter(AiReviewSession session, Long messageCursor) {
        long cursor = messageCursor == null ? 0L : messageCursor;
        return messageRepository.findBySessionIdAndIdGreaterThanOrderByCreatedAtAsc(session.getId(), cursor).stream()
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
