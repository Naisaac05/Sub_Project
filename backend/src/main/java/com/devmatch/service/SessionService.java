package com.devmatch.service;

import com.devmatch.dto.session.SessionCreateRequest;
import com.devmatch.dto.session.SessionResponse;
import com.devmatch.entity.MentoringSession;
import com.devmatch.entity.SessionStatus;
import com.devmatch.exception.InvalidSessionStateException;
import com.devmatch.exception.SessionAlreadyExistsException;
import com.devmatch.exception.SessionNotFoundException;
import com.devmatch.repository.MentoringSessionRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

    private final MentoringSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final GoogleCalendarService googleCalendarService;

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

        // 세션 생성
        // 참고: 실제로는 Matching 엔티티에서 mentorId, menteeId, category를 가져와야 하지만
        // Phase 3 Matching 엔티티는 팀원이 구현할 예정이므로,
        // 여기서는 요청자를 menteeId로 설정합니다.
        MentoringSession session = MentoringSession.builder()
                .matchingId(request.getMatchingId())
                .menteeId(userId)
                .mentorId(0L)  // Matching 연동 시 실제 멘토 ID로 교체
                .category("General")  // Matching 연동 시 실제 카테고리로 교체
                .sessionDate(request.getSessionDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .memo(request.getMemo())
                .build();

        MentoringSession saved = sessionRepository.save(session);

        // Google Calendar 이벤트 생성 시도
        try {
            // 사용자 이메일 조회
            String menteeEmail = userRepository.findById(userId)
                    .map(u -> u.getEmail())
                    .orElse("mentee@devmatch.kr");

            Map<String, String> calendarResult = googleCalendarService.createMentoringEvent(
                    "mentor@devmatch.kr",  // Matching 연동 시 실제 멘토 이메일로 교체
                    menteeEmail,
                    saved.getCategory(),
                    saved.getSessionDate(),
                    saved.getStartTime(),
                    saved.getEndTime(),
                    saved.getMemo()
            );

            if (calendarResult != null) {
                saved.updateMeetLink(calendarResult.get("meetLink"));
                saved.updateCalendarEventId(calendarResult.get("calendarEventId"));
                log.info("[Session] Google Calendar 연동 성공 — sessionId: {}", saved.getId());
            }
        } catch (Exception e) {
            // Calendar 연동 실패해도 세션은 유지
            log.warn("[Session] Google Calendar 연동 실패 (세션은 유지) — {}", e.getMessage());
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
