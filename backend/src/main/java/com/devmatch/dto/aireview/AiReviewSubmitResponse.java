package com.devmatch.dto.aireview;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AiReviewSubmitResponse {

    private String evaluation;
    private String feedback;
    private String nextQuestion;
    private boolean completed;
    private String summary;
    private List<AiReviewMessageResponse> messages;
}
