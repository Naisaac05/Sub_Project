package com.devmatch.dto.matching;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MatchingRequest {

    @NotNull(message = "멘토 ID는 필수입니다")
    private Long mentorId;

    @NotBlank(message = "매칭 분야는 필수입니다")
    private String category;

    private Long testResultId;

    @Size(max = 500, message = "신청 메시지는 500자 이하여야 합니다")
    private String message;
}
