package com.devmatch.dto.menteechange;

import com.devmatch.entity.MentorChangeRequest;
import com.devmatch.entity.MentorChangeRequestStatus;

import java.time.LocalDateTime;

public record MentorChangeRequestResponse(
        Long id,
        Long menteeId,
        Long currentMatchingId,
        Long currentMentorId,
        String reason,
        MentorChangeRequestStatus status,
        Long newMentorId,
        String rejectReason,
        Long decidedByAdminId,
        LocalDateTime createdAt,
        LocalDateTime respondedAt
) {
    public static MentorChangeRequestResponse from(MentorChangeRequest e) {
        return new MentorChangeRequestResponse(
                e.getId(), e.getMenteeId(), e.getCurrentMatchingId(), e.getCurrentMentorId(),
                e.getReason(), e.getStatus(), e.getNewMentorId(), e.getRejectReason(),
                e.getDecidedByAdminId(), e.getCreatedAt(), e.getRespondedAt());
    }
}
