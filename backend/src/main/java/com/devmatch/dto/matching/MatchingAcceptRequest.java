package com.devmatch.dto.matching;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MatchingAcceptRequest {

    @NotNull(message = "수락/거절 여부는 필수입니다")
    private Boolean accepted;

    @Size(max = 500, message = "거절 사유는 500자 이하여야 합니다")
    private String rejectedReason;
}
