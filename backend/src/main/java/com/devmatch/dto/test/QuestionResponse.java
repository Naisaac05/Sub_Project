package com.devmatch.dto.test;

import com.devmatch.entity.Question;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class QuestionResponse {

    private Long id;
    private String content;
    private List<String> options;
    private Integer score;
    private Integer orderIndex;

    public static QuestionResponse from(Question question) {
        return new QuestionResponse(
                question.getId(),
                question.getContent(),
                question.getOptions(),
                question.getScore(),
                question.getOrderIndex()
        );
    }
}
