package com.devmatch.dto.aireview;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AiReviewSubmitRequest {

    @Size(max = 700)
    private String answer;

    private String mode;

    private Long questionId;
}
