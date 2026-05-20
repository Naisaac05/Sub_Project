package com.devmatch.dto.aireview;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AiReviewSummaryResponse {

    private Long questionId;
    private String summary;
    private boolean overall;
    private List<AiReviewMessageResponse> messages;
}
