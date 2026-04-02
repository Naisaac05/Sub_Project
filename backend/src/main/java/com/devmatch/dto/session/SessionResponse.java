package com.devmatch.dto.session;

import com.devmatch.entity.MentoringSession;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class SessionResponse {

    private Long id;
    private Long matchingId;
    private Long menteeId;
    private String menteeName;
    private Long mentorId;
    private String mentorName;
    private String category;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String meetLink;
    private String memo;
    private LocalDateTime createdAt;

    public static SessionResponse from(MentoringSession session) {
        return new SessionResponse(
                session.getId(),
                session.getMatching().getId(),
                session.getMentee().getId(),
                session.getMentee().getName(),
                session.getMentor().getId(),
                session.getMentor().getName(),
                session.getCategory(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getStatus().name(),
                session.getMeetLink(),
                session.getMemo(),
                session.getCreatedAt()
        );
    }
}
