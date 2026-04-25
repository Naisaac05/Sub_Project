package com.devmatch.dto.admin.menteechange;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMentorChangeRejectRequest(
        @NotBlank(message = "반려 사유는 필수입니다")
        @Size(min = 1, max = 500, message = "반려 사유는 1~500자여야 합니다")
        String rejectReason
) {}
