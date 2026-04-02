package com.devmatch.service;

import com.devmatch.dto.test.*;
import com.devmatch.entity.*;
import com.devmatch.exception.TestNotFoundException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestService {

    private final TestRepository testRepository;
    private final QuestionRepository questionRepository;
    private final TestResultRepository testResultRepository;
    private final TestAnswerRepository testAnswerRepository;
    private final UserRepository userRepository;

    public List<TestListResponse> getTests(String category) {
        List<Test> tests;
        if (category != null && !category.isBlank()) {
            tests = testRepository.findByCategoryAndIsActiveTrue(category);
        } else {
            tests = testRepository.findByIsActiveTrue();
        }
        return tests.stream()
                .map(TestListResponse::from)
                .collect(Collectors.toList());
    }

    public TestDetailResponse getTestDetail(Long testId) {
        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new TestNotFoundException("테스트를 찾을 수 없습니다: " + testId));

        List<QuestionResponse> questions = questionRepository.findByTestIdOrderByOrderIndexAsc(testId)
                .stream()
                .map(QuestionResponse::from)
                .collect(Collectors.toList());

        return TestDetailResponse.of(test, questions);
    }

    @Transactional
    public TestResultResponse submitTest(Long userId, Long testId, TestSubmitRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        Test test = testRepository.findById(testId)
                .orElseThrow(() -> new TestNotFoundException("테스트를 찾을 수 없습니다: " + testId));

        List<Question> questions = questionRepository.findByTestIdOrderByOrderIndexAsc(testId);
        Map<Long, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getId, Function.identity()));

        // 1단계: 채점 (점수 먼저 계산)
        int totalScore = 0;
        int correctCount = 0;

        // 임시 채점 결과 저장용
        List<GradedAnswer> gradedAnswers = new ArrayList<>();

        for (AnswerRequest answerReq : request.getAnswers()) {
            Question question = questionMap.get(answerReq.getQuestionId());
            if (question == null) {
                continue;
            }
            boolean isCorrect = question.getCorrectAnswer().equals(answerReq.getSelectedAnswer());
            if (isCorrect) {
                totalScore += question.getScore();
                correctCount++;
            }
            gradedAnswers.add(new GradedAnswer(question, answerReq.getSelectedAnswer(), isCorrect));
        }

        boolean passed = totalScore >= test.getPassingScore();

        // 2단계: TestResult 저장
        TestResult testResult = TestResult.builder()
                .user(user)
                .test(test)
                .totalScore(totalScore)
                .correctCount(correctCount)
                .passed(passed)
                .submittedAt(LocalDateTime.now())
                .build();
        testResult = testResultRepository.save(testResult);

        // 3단계: TestAnswer 일괄 저장
        final TestResult savedResult = testResult;
        List<TestAnswer> answers = gradedAnswers.stream()
                .map(g -> TestAnswer.builder()
                        .testResult(savedResult)
                        .question(g.question)
                        .selectedAnswer(g.selectedAnswer)
                        .isCorrect(g.isCorrect)
                        .build())
                .collect(Collectors.toList());
        testAnswerRepository.saveAll(answers);

        return TestResultResponse.from(testResult);
    }

    public List<TestResultResponse> getMyResults(Long userId) {
        return testResultRepository.findByUserIdOrderBySubmittedAtDesc(userId)
                .stream()
                .map(TestResultResponse::from)
                .collect(Collectors.toList());
    }

    // 채점 중간 결과를 담는 내부 레코드
    private record GradedAnswer(Question question, int selectedAnswer, boolean isCorrect) {}
}
