package com.devmatch.dto.test;

import com.devmatch.entity.Test;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TestListResponse {

    private Long id;
    private String title;
    private String category;
    private String difficulty;
    private Integer timeLimit;
    private Integer questionCount;
    private Integer passingScore;

    public static TestListResponse from(Test test) {
        return new TestListResponse(
                test.getId(),
                test.getTitle(),
                test.getCategory(),
                test.getDifficulty().name(),
                test.getTimeLimit(),
                test.getQuestionCount(),
                test.getPassingScore()
        );
    }
}
