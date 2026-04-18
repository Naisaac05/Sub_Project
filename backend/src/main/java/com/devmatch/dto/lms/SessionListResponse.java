package com.devmatch.dto.lms;

import com.devmatch.entity.MentoringSession;
import com.devmatch.entity.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter @AllArgsConstructor @Builder
public class SessionListResponse {
    private Long id;
    private Long matchingId;
    private Long menteeId;
    private Long mentorId;
    private String category;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private SessionStatus status;
    private String title;
    private String meetLink;
    private String memo;
    private boolean hasPendingChangeRequest;
    private LocalDateTime createdAt;

    public static SessionListResponse from(MentoringSession s, boolean hasPendingChange) {
        return SessionListResponse.builder()
                .id(s.getId())
                .matchingId(s.getMatchingId())
                .menteeId(s.getMenteeId())
                .mentorId(s.getMentorId())
                .category(s.getCategory())
                .sessionDate(s.getSessionDate())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .status(s.getStatus())
                .title(s.getTitle())
                .meetLink(s.getMeetLink())
                .memo(s.getMemo())
                .hasPendingChangeRequest(hasPendingChange)
                .createdAt(s.getCreatedAt())
                .build();
    }
}
