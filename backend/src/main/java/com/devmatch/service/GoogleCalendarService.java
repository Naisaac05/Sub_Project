package com.devmatch.service;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

/**
 * Google Calendar API를 통해 멘토링 일정을 생성/삭제합니다.
 * Calendar bean이 null이면 스텁 모드로 동작합니다 (로그만 출력).
 */
@Slf4j
@Service
public class GoogleCalendarService {

    private final Calendar calendar;

    public GoogleCalendarService(@Nullable Calendar calendar) {
        this.calendar = calendar;
        if (calendar == null) {
            log.info("[GoogleCalendar] 스텁 모드로 초기화됨 — credentials 없이 동작합니다.");
        } else {
            log.info("[GoogleCalendar] 실제 Google Calendar API 클라이언트로 초기화됨.");
        }
    }

    /**
     * Google Calendar에 멘토링 세션 이벤트를 생성하고
     * Google Meet 링크를 자동 생성합니다.
     *
     * @return Map with "meetLink" and "calendarEventId", or null if stub mode
     */
    public Map<String, String> createMentoringEvent(
            String mentorEmail,
            String menteeEmail,
            String category,
            LocalDate sessionDate,
            LocalTime startTime,
            LocalTime endTime,
            String memo
    ) {
        // 스텁 모드
        if (calendar == null) {
            log.info("[GoogleCalendar STUB] 이벤트 생성 요청 — mentor: {}, mentee: {}, date: {}, time: {}-{}",
                    mentorEmail, menteeEmail, sessionDate, startTime, endTime);
            return null;
        }

        try {
            // 이벤트 기본 정보
            Event event = new Event()
                    .setSummary("[DevMatch] " + category + " 멘토링 세션")
                    .setDescription(memo != null ? memo : "DevMatch 멘토링 세션");

            // 시간 설정 (Asia/Seoul)
            ZoneId seoulZone = ZoneId.of("Asia/Seoul");
            ZonedDateTime start = ZonedDateTime.of(sessionDate, startTime, seoulZone);
            ZonedDateTime end = ZonedDateTime.of(sessionDate, endTime, seoulZone);

            event.setStart(new EventDateTime()
                    .setDateTime(new DateTime(start.toInstant().toEpochMilli()))
                    .setTimeZone("Asia/Seoul"));
            event.setEnd(new EventDateTime()
                    .setDateTime(new DateTime(end.toInstant().toEpochMilli()))
                    .setTimeZone("Asia/Seoul"));

            // 참석자 (멘토 + 멘티)
            EventAttendee mentor = new EventAttendee().setEmail(mentorEmail);
            EventAttendee mentee = new EventAttendee().setEmail(menteeEmail);
            event.setAttendees(Arrays.asList(mentor, mentee));

            // Google Meet 자동 생성
            ConferenceData conferenceData = new ConferenceData();
            CreateConferenceRequest createRequest = new CreateConferenceRequest()
                    .setRequestId(UUID.randomUUID().toString())
                    .setConferenceSolutionKey(new ConferenceSolutionKey().setType("hangoutsMeet"));
            conferenceData.setCreateRequest(createRequest);
            event.setConferenceData(conferenceData);

            // 이벤트 생성 API 호출
            Event created = calendar.events()
                    .insert("primary", event)
                    .setConferenceDataVersion(1)
                    .setSendUpdates("all")
                    .execute();

            String meetLink = null;
            if (created.getConferenceData() != null &&
                created.getConferenceData().getEntryPoints() != null) {
                meetLink = created.getConferenceData().getEntryPoints().stream()
                        .filter(ep -> "video".equals(ep.getEntryPointType()))
                        .map(ep -> ep.getUri())
                        .findFirst()
                        .orElse(null);
            }

            log.info("[GoogleCalendar] 이벤트 생성 완료 — eventId: {}, meetLink: {}",
                    created.getId(), meetLink);

            return Map.of(
                    "calendarEventId", created.getId(),
                    "meetLink", meetLink != null ? meetLink : ""
            );

        } catch (IOException e) {
            log.error("[GoogleCalendar] 이벤트 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Google Calendar에서 이벤트를 삭제합니다.
     */
    public void deleteEvent(String calendarEventId) {
        if (calendar == null) {
            log.info("[GoogleCalendar STUB] 이벤트 삭제 요청 — eventId: {}", calendarEventId);
            return;
        }

        try {
            calendar.events()
                    .delete("primary", calendarEventId)
                    .setSendUpdates("all")
                    .execute();
            log.info("[GoogleCalendar] 이벤트 삭제 완료 — eventId: {}", calendarEventId);
        } catch (IOException e) {
            log.error("[GoogleCalendar] 이벤트 삭제 실패: {}", e.getMessage());
        }
    }
}
