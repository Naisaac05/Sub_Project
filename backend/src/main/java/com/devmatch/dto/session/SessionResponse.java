package com.devmatch.dto.session;

import com.devmatch.entity.MentoringSession;
import com.devmatch.entity.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@AllArgsConstructor
@Builder
public class SessionResponse {

    private Long id;
    private Long matchingId;
    private Long menteeId;
    private Long mentorId;
    private String category;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private SessionStatus status;
    private String meetLink;
    private String calendarEventId;
    private String memo;
    private LocalDateTime createdAt;

    public static SessionResponse from(MentoringSession session) {
        return SessionResponse.builder()
                .id(session.getId())
                .matchingId(session.getMatchingId())
                .menteeId(session.getMenteeId())
                .mentorId(session.getMentorId())
                .category(session.getCategory())
                .sessionDate(session.getSessionDate())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .status(session.getStatus())
                .meetLink(session.getMeetLink())
                .calendarEventId(session.getCalendarEventId())
                .memo(session.getMemo())
                .createdAt(session.getCreatedAt())
                .build();
    }
}
