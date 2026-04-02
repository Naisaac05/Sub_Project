package com.devmatch.service;

import com.devmatch.dto.session.SessionCreateRequest;
import com.devmatch.dto.session.SessionResponse;
import com.devmatch.entity.*;
import com.devmatch.exception.*;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentoringSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

    private final MentoringSessionRepository sessionRepository;
    private final MatchingRepository matchingRepository;
    private final GoogleCalendarService googleCalendarService;

    @Transactional
    public SessionResponse createSession(Long userId, SessionCreateRequest request) {
        Matching matching = matchingRepository.findById(request.getMatchingId())
                .orElseThrow(() -> new MatchingNotFoundException("매칭을 찾을 수 없습니다"));

        // 매칭 ACCEPTED 상태 확인
        if (matching.getStatus() != MatchingStatus.ACCEPTED) {
            throw new InvalidSessionStateException("수락된 매칭에 대해서만 세션을 생성할 수 있습니다");
        }

        // 매칭 당사자 확인
        if (!matching.getMentee().getId().equals(userId) && !matching.getMentor().getId().equals(userId)) {
            throw new UnauthorizedMatchingException("본인의 매칭에 대해서만 세션을 생성할 수 있습니다");
        }

        // 중복 세션 확인
        if (sessionRepository.existsByMatchingId(matching.getId())) {
            throw new SessionAlreadyExistsException("이미 해당 매칭에 세션이 존재합니다");
        }

        MentoringSession session = MentoringSession.builder()
                .matching(matching)
                .mentee(matching.getMentee())
                .mentor(matching.getMentor())
                .category(matching.getCategory())
                .sessionDate(request.getSessionDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .memo(request.getMemo())
                .build();

        session = sessionRepository.save(session);

        // Google Calendar 연동 (실패해도 세션 생성은 유지)
        GoogleCalendarService.GoogleEventResult result = googleCalendarService.createEvent(session);
        if (result != null) {
            session.updateGoogleCalendarInfo(result.meetLink(), result.calendarEventId());
        }

        return SessionResponse.from(session);
    }

    public List<SessionResponse> getMySessions(Long userId) {
        List<MentoringSession> mentee = sessionRepository.findByMenteeIdOrderBySessionDateDesc(userId);
        List<MentoringSession> mentor = sessionRepository.findByMentorIdOrderBySessionDateDesc(userId);

        List<MentoringSession> all = new ArrayList<>();
        all.addAll(mentee);
        all.addAll(mentor);

        // 중복 제거 (멘토이면서 멘티인 경우는 없지만 안전하게) + 날짜 내림차순 정렬
        return all.stream()
                .distinct()
                .sorted((a, b) -> b.getSessionDate().compareTo(a.getSessionDate()))
                .map(SessionResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public SessionResponse cancelSession(Long userId, Long sessionId) {
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다"));

        // 당사자 확인
        if (!session.getMentee().getId().equals(userId) && !session.getMentor().getId().equals(userId)) {
            throw new UnauthorizedMatchingException("본인의 세션만 취소할 수 있습니다");
        }

        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new InvalidSessionStateException("예정된 세션만 취소할 수 있습니다");
        }

        session.cancel();

        // Google Calendar 이벤트 삭제
        googleCalendarService.deleteEvent(session.getCalendarEventId());

        return SessionResponse.from(session);
    }

    @Transactional
    public SessionResponse completeSession(Long userId, Long sessionId) {
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다"));

        // 당사자 확인
        if (!session.getMentee().getId().equals(userId) && !session.getMentor().getId().equals(userId)) {
            throw new UnauthorizedMatchingException("본인의 세션만 완료 처리할 수 있습니다");
        }

        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new InvalidSessionStateException("예정된 세션만 완료 처리할 수 있습니다");
        }

        session.complete();
        return SessionResponse.from(session);
    }
}
