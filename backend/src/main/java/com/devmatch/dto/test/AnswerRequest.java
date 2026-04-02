package com.devmatch.dto.test;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnswerRequest {

    @NotNull(message = "문제 ID는 필수입니다")
    private Long questionId;

    @NotNull(message = "답안은 필수입니다")
    @Min(value = 0, message = "답안은 0~3 범위여야 합니다")
    @Max(value = 3, message = "답안은 0~3 범위여야 합니다")
    private Integer selectedAnswer;
}
