package com.devmatch.service;

import com.devmatch.dto.session.SessionCreateRequest;
import com.devmatch.dto.session.SessionResponse;
import com.devmatch.entity.Matching;
import com.devmatch.entity.MentoringSession;
import com.devmatch.entity.SessionStatus;
import com.devmatch.exception.*;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentoringSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

    private final MentoringSessionRepository sessionRepository;
    private final MatchingRepository matchingRepository;
    private final GoogleCalendarService googleCalendarService;
    private final JitsiMeetService jitsiMeetService;

    /**
     * 멘토링 세션 생성
     * 1. 중복 세션 확인
     * 2. MentoringSession 생성 (status: SCHEDULED)
     * 3. Google Calendar 이벤트 생성 + Meet 링크 저장
     */
    @Transactional
    public SessionResponse createSession(Long userId, SessionCreateRequest request) {
        // 중복 세션 확인
        if (sessionRepository.existsByMatchingId(request.getMatchingId())) {
            throw new SessionAlreadyExistsException("이미 해당 매칭에 대한 세션이 존재합니다");
        }

        // 매칭 정보 조회
        Matching matching = matchingRepository.findById(request.getMatchingId())
                .orElseThrow(() -> new MatchingNotFoundException("매칭을 찾을 수 없습니다: " + request.getMatchingId()));

        // 권한 확인 (멘티 혹은 멘토만 세션 생성 가능)
        if (!matching.getMentee().getId().equals(userId) && !matching.getMentor().getId().equals(userId)) {
            throw new InvalidSessionStateException("해당 매칭의 당사자만 세션을 생성할 수 있습니다");
        }

        // 세션 엔티티 생성
        MentoringSession session = MentoringSession.builder()
                .matchingId(matching.getId())
                .menteeId(matching.getMentee().getId())
                .mentorId(matching.getMentor().getId())
                .category(matching.getCategory())
                .sessionDate(request.getSessionDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .memo(request.getMemo())
                .build();

        MentoringSession saved = sessionRepository.save(session);

        // 1. Google Calendar 이벤트 & Meet 링크 생성 시도
        var calendarResult = googleCalendarService.createMentoringEvent(
                matching.getMentor().getEmail(),
                matching.getMentee().getEmail(),
                matching.getCategory(),
                saved.getSessionDate(),
                saved.getStartTime(),
                saved.getEndTime(),
                saved.getMemo()
        );

        if (calendarResult != null && calendarResult.get("meetLink") != null && !calendarResult.get("meetLink").isBlank()) {
            saved.updateMeetLink(calendarResult.get("meetLink"));
            saved.updateCalendarEventId(calendarResult.get("calendarEventId"));
            log.info("[Session] Google Meet 링크 생성 완료 — sessionId: {}, link: {}", saved.getId(), saved.getMeetLink());
        } else {
            // 2. Google Calendar 실패 시 Jitsi Meet로 대체 (Fallback)
            String meetLink = jitsiMeetService.generateMeetLink(
                    saved.getMatchingId(), saved.getSessionDate());
            saved.updateMeetLink(meetLink);
            log.info("[Session] Google Meet 생성 실패로 Jitsi Meet 대체 — sessionId: {}, link: {}", saved.getId(), meetLink);
        }

        return SessionResponse.from(saved);
    }

    /**
     * 내 세션 목록 조회 (멘티 + 멘토 모두)
     */
    public List<SessionResponse> getMySessions(Long userId) {
        return sessionRepository.findByMenteeIdOrMentorIdOrderBySessionDateDesc(userId, userId)
                .stream()
                .map(SessionResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 세션 취소
     */
    @Transactional
    public SessionResponse cancelSession(Long userId, Long sessionId) {
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다: " + sessionId));

        // 본인 세션인지 확인
        if (!session.getMenteeId().equals(userId) && !session.getMentorId().equals(userId)) {
            throw new InvalidSessionStateException("본인의 세션만 취소할 수 있습니다");
        }

        // 이미 취소/완료된 세션 확인
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new InvalidSessionStateException("예정된 세션만 취소할 수 있습니다. 현재 상태: " + session.getStatus());
        }

        session.cancel();

        // Google Calendar 이벤트 삭제
        if (session.getCalendarEventId() != null) {
            googleCalendarService.deleteEvent(session.getCalendarEventId());
        }

        return SessionResponse.from(session);
    }

    /**
     * 세션 완료 처리
     */
    @Transactional
    public SessionResponse completeSession(Long userId, Long sessionId) {
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다: " + sessionId));

        if (!session.getMentorId().equals(userId)) {
            throw new InvalidSessionStateException("멘토만 세션을 완료 처리할 수 있습니다");
        }

        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new InvalidSessionStateException("예정된 세션만 완료 처리할 수 있습니다. 현재 상태: " + session.getStatus());
        }

        session.complete();
        return SessionResponse.from(session);
    }
}
