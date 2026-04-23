package com.devmatch.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class MentorSwapRequest {

    @NotNull(message = "새 멘토 사용자 ID 가 필요합니다")
    private Long newMentorId;

    @NotBlank(message = "사유는 필수입니다")
    @Size(min = 10, max = 500, message = "사유는 10~500자여야 합니다")
    private String reason;
}
