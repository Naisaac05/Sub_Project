package com.devmatch.dto.mentor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class MentorApplyRequest {

    @NotEmpty(message = "전문 분야는 최소 1개 이상 입력해야 합니다")
    private List<String> specialty;

    @NotNull(message = "경력 연수는 필수입니다")
    @Min(value = 1, message = "경력은 1년 이상이어야 합니다")
    private Integer careerYears;

    private String company;

    @Size(max = 1000, message = "자기 소개는 1000자 이하여야 합니다")
    private String bio;
}
