package com.devmatch.dto.test;

import com.devmatch.entity.Test;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class TestDetailResponse {

    private Long id;
    private String title;
    private String description;
    private String category;
    private String difficulty;
    private Integer timeLimit;
    private Integer passingScore;
    private List<QuestionResponse> questions;

    public static TestDetailResponse of(Test test, List<QuestionResponse> questions) {
        return new TestDetailResponse(
                test.getId(),
                test.getTitle(),
                test.getDescription(),
                test.getCategory(),
                test.getDifficulty().name(),
                test.getTimeLimit(),
                test.getPassingScore(),
                questions
        );
    }
}
