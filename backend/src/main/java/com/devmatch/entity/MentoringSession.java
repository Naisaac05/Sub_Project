package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "mentoring_sessions")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class MentoringSession {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matching_id", nullable = false, unique = true)
    private Long matchingId;

    @Column(name = "mentee_id", nullable = false)
    private Long menteeId;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SessionStatus status = SessionStatus.SCHEDULED;

    @Column(name = "meet_link", length = 500)
    private String meetLink;

    @Column(name = "calendar_event_id", length = 200)
    private String calendarEventId;

    @Column(length = 1000)
    private String memo;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // — 변경 메서드 —

    public void cancel() {
        this.status = SessionStatus.CANCELLED;
    }

    public void complete() {
        this.status = SessionStatus.COMPLETED;
    }

    public void updateMeetLink(String meetLink) {
        this.meetLink = meetLink;
    }

    public void updateCalendarEventId(String calendarEventId) {
        this.calendarEventId = calendarEventId;
    }
}
