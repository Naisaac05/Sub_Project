package com.devmatch.dto.test;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class TestSubmitRequest {

    @NotEmpty(message = "답안 목록은 비어있을 수 없습니다")
    @Valid
    private List<AnswerRequest> answers;
}
