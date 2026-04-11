package com.devmatch.dto.lms;

import com.devmatch.entity.ChangeRequestStatus;
import com.devmatch.entity.SessionChangeRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter @AllArgsConstructor @Builder
public class ChangeRequestResponse {
    private Long id;
    private Long sessionId;
    private Long requesterId;
    private LocalDate newDate;
    private LocalTime newStartTime;
    private LocalTime newEndTime;
    private String reason;
    private ChangeRequestStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;

    public static ChangeRequestResponse from(SessionChangeRequest r) {
        return ChangeRequestResponse.builder()
                .id(r.getId())
                .sessionId(r.getSessionId())
                .requesterId(r.getRequesterId())
                .newDate(r.getNewDate())
                .newStartTime(r.getNewStartTime())
                .newEndTime(r.getNewEndTime())
                .reason(r.getReason())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .respondedAt(r.getRespondedAt())
                .build();
    }
}
