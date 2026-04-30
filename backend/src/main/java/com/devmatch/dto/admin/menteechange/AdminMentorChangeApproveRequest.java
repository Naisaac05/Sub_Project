package com.devmatch.dto.admin.menteechange;

import jakarta.validation.constraints.NotNull;

public record AdminMentorChangeApproveRequest(
        @NotNull(message = "newMentorUserId 는 필수입니다") Long newMentorUserId
) {}
