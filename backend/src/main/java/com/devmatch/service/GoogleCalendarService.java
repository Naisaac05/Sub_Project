package com.devmatch.service;

import com.devmatch.entity.MentoringSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Google Calendar & Meet 연동 서비스.
 *
 * 현재는 Google Cloud Console 설정 전이므로 스텁 구현입니다.
 * 실제 Google API 연동 시 이 클래스의 메서드 내부만 교체하면 됩니다.
 *
 * 실제 구현 시 필요한 작업:
 * 1. Google Cloud Console에서 Calendar API 활성화
 * 2. OAuth2 또는 Service Account 인증 설정
 * 3. com.google.api.services.calendar.Calendar 클라이언트 초기화
 * 4. Event 생성 시 conferenceDataVersion=1로 Meet 링크 자동 생성
 */
@Slf4j
@Service
public class GoogleCalendarService {

    /**
     * Google Calendar 이벤트를 생성하고 Meet 링크를 반환합니다.
     *
     * @param session 멘토링 세션 정보
     * @return 생성 결과 (meetLink, calendarEventId) 또는 실패 시 null
     */
    public GoogleEventResult createEvent(MentoringSession session) {
        try {
            // TODO: 실제 Google Calendar API 연동 시 아래 코드를 교체
            //
            // Calendar service = getCalendarService();
            // Event event = new Event()
            //     .setSummary("DevMatch 멘토링 - " + session.getCategory())
            //     .setDescription("멘토: " + session.getMentor().getName()
            //         + " / 멘티: " + session.getMentee().getName())
            //     .setStart(new EventDateTime()
            //         .setDateTime(toGoogleDateTime(session.getSessionDate(), session.getStartTime()))
            //         .setTimeZone("Asia/Seoul"))
            //     .setEnd(new EventDateTime()
            //         .setDateTime(toGoogleDateTime(session.getSessionDate(), session.getEndTime()))
            //         .setTimeZone("Asia/Seoul"))
            //     .setAttendees(List.of(
            //         new EventAttendee().setEmail(session.getMentor().getEmail()),
            //         new EventAttendee().setEmail(session.getMentee().getEmail())))
            //     .setConferenceData(new ConferenceData()
            //         .setCreateRequest(new CreateConferenceRequest()
            //             .setRequestId(UUID.randomUUID().toString())
            //             .setConferenceSolutionKey(new ConferenceSolutionKey().setType("hangoutsMeet"))));
            //
            // Event created = service.events().insert("primary", event)
            //     .setConferenceDataVersion(1)
            //     .execute();
            //
            // String meetLink = created.getHangoutLink();
            // String eventId = created.getId();

            log.info("Google Calendar 이벤트 생성 요청 — 세션 ID: {}, 날짜: {}, 시간: {}-{}",
                    session.getId(), session.getSessionDate(),
                    session.getStartTime(), session.getEndTime());

            // 스텁: Google API 미설정 상태에서는 null 반환
            log.warn("Google Calendar API가 설정되지 않았습니다. Meet 링크 없이 세션이 생성됩니다.");
            return null;

        } catch (Exception e) {
            log.error("Google Calendar 이벤트 생성 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Google Calendar 이벤트를 삭제합니다.
     *
     * @param calendarEventId 삭제할 이벤트 ID
     */
    public void deleteEvent(String calendarEventId) {
        if (calendarEventId == null) {
            return;
        }
        try {
            // TODO: 실제 Google Calendar API 연동 시 아래 코드를 교체
            // Calendar service = getCalendarService();
            // service.events().delete("primary", calendarEventId).execute();

            log.info("Google Calendar 이벤트 삭제 요청 — 이벤트 ID: {}", calendarEventId);
            log.warn("Google Calendar API가 설정되지 않았습니다. 이벤트 삭제를 건너뜁니다.");

        } catch (Exception e) {
            log.error("Google Calendar 이벤트 삭제 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * Google Calendar 이벤트 생성 결과
     */
    public record GoogleEventResult(String calendarEventId, String meetLink) {}
}
