package com.devmatch.dto.mentor;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class MentorApplyRequest {

    @NotEmpty(message = "멘토링 코스를 최소 1개 이상 선택해야 합니다")
    private List<String> courseKeys;

    private List<String> techStack;

    @NotNull(message = "경력 연수는 필수입니다")
    @Min(value = 1, message = "경력은 1년 이상이어야 합니다")
    private Integer careerYears;

    @Size(max = 100)
    private String company;

    @Size(max = 100)
    private String jobTitle;

    @Size(max = 500)
    private String portfolioUrl;

    @Size(max = 200)
    private String education;

    private List<String> certifications;

    @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED|ANY", message = "선호 멘티 수준 값이 유효하지 않습니다")
    private String preferredMenteeLevel;

    @Size(max = 1000, message = "자기 소개는 1000자 이하여야 합니다")
    private String bio;
}
