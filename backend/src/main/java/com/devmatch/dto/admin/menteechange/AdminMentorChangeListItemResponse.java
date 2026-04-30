package com.devmatch.dto.admin.menteechange;

import com.devmatch.entity.MentorChangeRequestStatus;

import java.time.LocalDateTime;

public record AdminMentorChangeListItemResponse(
        Long id,
        Long menteeId,
        String menteeName,
        String menteeEmail,
        Long currentMentorId,
        String currentMentorName,
        String reasonPreview,
        MentorChangeRequestStatus status,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {}
