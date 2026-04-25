package com.devmatch.dto.admin.menteechange;

import com.devmatch.entity.MentorChangeRequestStatus;

import java.time.LocalDateTime;

public record AdminMentorChangeDetailResponse(
        Long id,
        Long menteeId,
        String menteeName,
        String menteeEmail,
        Long currentMatchingId,
        String currentCategory,
        Long currentMentorId,
        String currentMentorName,
        String reason,
        MentorChangeRequestStatus status,
        Long newMentorId,
        String newMentorName,
        String rejectReason,
        Long decidedByAdminId,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {}
