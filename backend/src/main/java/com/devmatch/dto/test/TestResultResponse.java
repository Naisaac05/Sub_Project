package com.devmatch.dto.test;

import com.devmatch.entity.TestResult;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class TestResultResponse {

    private Long id;
    private Long testId;
    private String testTitle;
    private String category;
    private Integer totalScore;
    private Integer correctCount;
    private Integer questionCount;
    private Boolean passed;
    private LocalDateTime submittedAt;

    public static TestResultResponse from(TestResult result) {
        return new TestResultResponse(
                result.getId(),
                result.getTest().getId(),
                result.getTest().getTitle(),
                result.getTest().getCategory(),
                result.getTotalScore(),
                result.getCorrectCount(),
                result.getTest().getQuestionCount(),
                result.getPassed(),
                result.getSubmittedAt()
        );
    }
}
