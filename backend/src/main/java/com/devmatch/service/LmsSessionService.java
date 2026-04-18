package com.devmatch.service;

import com.devmatch.dto.lms.*;
import com.devmatch.entity.*;
import com.devmatch.exception.*;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LmsSessionService {

    private final LmsAccessService lmsAccessService;
    private final MentoringSessionRepository sessionRepository;
    private final MentorTimeSlotRepository timeSlotRepository;
    private final SessionChangeRequestRepository changeRequestRepository;
    private final GoogleCalendarService googleCalendarService;
    private final JitsiMeetService jitsiMeetService;

    // ─── Sessions ───

    public List<SessionListResponse> getSessions(Long userId, Long matchingId) {
        lmsAccessService.validateAccess(userId, matchingId);
        List<MentoringSession> sessions = sessionRepository.findByMatchingIdOrderBySessionDateDesc(matchingId);
        List<Long> sessionIds = sessions.stream().map(MentoringSession::getId).toList();
        Set<Long> sessionsWithPendingChange = changeRequestRepository
                .findBySessionIdInOrderByCreatedAtDesc(sessionIds).stream()
                .filter(cr -> cr.getStatus() == ChangeRequestStatus.PENDING)
                .map(SessionChangeRequest::getSessionId)
                .collect(Collectors.toSet());
        return sessions.stream()
                .map(s -> SessionListResponse.from(s, sessionsWithPendingChange.contains(s.getId())))
                .toList();
    }

    @Transactional
    public SessionListResponse completeSession(Long userId, Long matchingId, Long sessionId) {
        lmsAccessService.validateMentorAccess(userId, matchingId);
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다: " + sessionId));
        if (!session.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 세션이 아닙니다");
        }
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new InvalidSessionStateException("예정 상태의 세션만 완료 처리할 수 있습니다");
        }
        session.complete();
        return SessionListResponse.from(session, false);
    }

    @Transactional
    public SessionListResponse cancelSession(Long userId, Long matchingId, Long sessionId) {
        lmsAccessService.validateAccess(userId, matchingId);
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다: " + sessionId));
        if (!session.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 세션이 아닙니다");
        }
        if (session.getStatus() != SessionStatus.SCHEDULED && session.getStatus() != SessionStatus.PENDING) {
            throw new InvalidSessionStateException("예정 또는 승인 대기 상태의 세션만 취소할 수 있습니다");
        }
        if (hasStarted(session)) {
            throw new InvalidSessionStateException("이미 시작된 세션은 취소할 수 없습니다");
        }
        session.cancel();
        unbookMatchingSlot(session);
        return SessionListResponse.from(session, false);
    }

    private void unbookMatchingSlot(MentoringSession session) {
        timeSlotRepository
                .findByMatchingIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(
                        session.getMatchingId(), session.getSessionDate(), session.getSessionDate())
                .stream()
                .filter(s -> s.getStartTime().equals(session.getStartTime())
                        && s.getEndTime().equals(session.getEndTime())
                        && s.getIsBooked())
                .findFirst()
                .ifPresent(MentorTimeSlot::unbook);
    }

    private boolean hasStarted(MentoringSession session) {
        return LocalDateTime.of(session.getSessionDate(), session.getStartTime())
                .isBefore(LocalDateTime.now());
    }

    // ─── Time Slots ───

    public List<TimeSlotResponse> getSlots(Long userId, Long matchingId, String month) {
        lmsAccessService.validateAccess(userId, matchingId);
        YearMonth ym = YearMonth.parse(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        return timeSlotRepository
                .findByMatchingIdAndSlotDateBetweenOrderBySlotDateAscStartTimeAsc(matchingId, start, end)
                .stream().map(TimeSlotResponse::from).toList();
    }

    public List<TimeSlotResponse> getAvailableSlots(Long userId, Long matchingId, LocalDate date) {
        lmsAccessService.validateAccess(userId, matchingId);
        return timeSlotRepository
                .findByMatchingIdAndSlotDateAndIsBookedFalseAndProposedByMenteeFalseOrderByStartTimeAsc(matchingId, date)
                .stream().map(TimeSlotResponse::from).toList();
    }

    public List<TimeSlotResponse> getProposedSlots(Long userId, Long matchingId) {
        lmsAccessService.validateAccess(userId, matchingId);
        return timeSlotRepository
                .findByMatchingIdAndProposedByMenteeTrueOrderBySlotDateAscStartTimeAsc(matchingId)
                .stream().map(TimeSlotResponse::from).toList();
    }

    @Transactional
    public TimeSlotResponse proposeSlot(Long userId, Long matchingId, TimeSlotCreateRequest request) {
        Matching matching = lmsAccessService.validateMenteeAccess(userId, matchingId);
        if (request.getEndTime().isBefore(request.getStartTime()) || request.getEndTime().equals(request.getStartTime())) {
            throw new InvalidSessionStateException("종료 시간은 시작 시간 이후여야 합니다");
        }
        if (timeSlotRepository.existsByMatchingIdAndSlotDateAndStartTimeAndEndTime(
                matchingId, request.getSlotDate(), request.getStartTime(), request.getEndTime())) {
            throw new SessionAlreadyExistsException("동일한 시간대의 슬롯이 이미 존재합니다");
        }
        MentorTimeSlot slot = MentorTimeSlot.builder()
                .mentorId(matching.getMentor().getId())
                .matchingId(matchingId)
                .slotDate(request.getSlotDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .proposedByMentee(true)
                .build();
        return TimeSlotResponse.from(timeSlotRepository.save(slot));
    }

    @Transactional
    public TimeSlotResponse createSlot(Long userId, Long matchingId, TimeSlotCreateRequest request) {
        lmsAccessService.validateMentorAccess(userId, matchingId);
        if (request.getEndTime().isBefore(request.getStartTime()) || request.getEndTime().equals(request.getStartTime())) {
            throw new InvalidSessionStateException("종료 시간은 시작 시간 이후여야 합니다");
        }
        if (timeSlotRepository.existsByMatchingIdAndSlotDateAndStartTimeAndEndTime(
                matchingId, request.getSlotDate(), request.getStartTime(), request.getEndTime())) {
            throw new SessionAlreadyExistsException("동일한 시간대의 슬롯이 이미 존재합니다");
        }
        MentorTimeSlot slot = MentorTimeSlot.builder()
                .mentorId(userId)
                .matchingId(matchingId)
                .slotDate(request.getSlotDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .build();
        return TimeSlotResponse.from(timeSlotRepository.save(slot));
    }

    @Transactional
    public void deleteSlot(Long userId, Long matchingId, Long slotId) {
        Matching matching = lmsAccessService.validateAccess(userId, matchingId);
        MentorTimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new TimeSlotNotFoundException("슬롯을 찾을 수 없습니다: " + slotId));
        if (!slot.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 슬롯이 아닙니다");
        }
        if (slot.getIsBooked()) {
            throw new InvalidSessionStateException("이미 예약된 슬롯은 삭제할 수 없습니다");
        }
        boolean isMentor = matching.getMentor().getId().equals(userId);
        if (slot.getProposedByMentee() && isMentor) {
            // mentor can dismiss mentee proposals
        } else if (!slot.getProposedByMentee() && !isMentor) {
            throw new LmsAccessDeniedException("멘토만 가용시간 슬롯을 삭제할 수 있습니다");
        }
        timeSlotRepository.delete(slot);
    }

    // ─── Mentor direct session creation (free time) ───

    @Transactional
    public SessionListResponse createSessionDirect(Long userId, Long matchingId, DirectSessionCreateRequest request) {
        Matching matching = lmsAccessService.validateMentorAccess(userId, matchingId);
        if (request.getEndTime().isBefore(request.getStartTime())
                || request.getEndTime().equals(request.getStartTime())) {
            throw new InvalidSessionStateException("종료 시간은 시작 시간 이후여야 합니다");
        }
        MentoringSession session = MentoringSession.builder()
                .matchingId(matchingId)
                .menteeId(matching.getMentee().getId())
                .mentorId(userId)
                .category(matching.getCategory())
                .sessionDate(request.getSessionDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .memo(request.getMemo())
                .build();

        MentoringSession saved = sessionRepository.save(session);
        generateAndSaveMeetLink(matching, saved);
        return SessionListResponse.from(saved, false);
    }

    private void generateAndSaveMeetLink(Matching matching, MentoringSession session) {
        var calendarResult = googleCalendarService.createMentoringEvent(
                matching.getMentor().getEmail(),
                matching.getMentee().getEmail(),
                matching.getCategory(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getMemo()
        );

        if (calendarResult != null && calendarResult.get("meetLink") != null && !calendarResult.get("meetLink").isBlank()) {
            session.updateMeetLink(calendarResult.get("meetLink"));
            session.updateCalendarEventId(calendarResult.get("calendarEventId"));
            log.info("[LMS Session] Google Meet 링크 생성 완료 — sessionId: {}, link: {}", session.getId(), session.getMeetLink());
        } else {
            String meetLink = jitsiMeetService.generateMeetLink(
                    session.getMatchingId(), session.getSessionDate());
            session.updateMeetLink(meetLink);
            log.info("[LMS Session] Google Meet 생성 실패로 Jitsi Meet 대체 — sessionId: {}, link: {}", session.getId(), meetLink);
        }
    }

    // ─── Booking ───

    @Transactional
    public SessionListResponse bookSession(Long userId, Long matchingId, BookSessionRequest request) {
        Matching matching = lmsAccessService.validateMenteeAccess(userId, matchingId);
        MentorTimeSlot slot = timeSlotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new TimeSlotNotFoundException("슬롯을 찾을 수 없습니다: " + request.getSlotId()));
        if (!slot.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 슬롯이 아닙니다");
        }
        if (slot.getIsBooked()) {
            throw new InvalidSessionStateException("이미 예약된 슬롯입니다");
        }
        slot.book();
        MentoringSession session = MentoringSession.builder()
                .matchingId(matchingId)
                .menteeId(userId)
                .mentorId(matching.getMentor().getId())
                .category(matching.getCategory())
                .sessionDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .memo(request.getMemo())
                .status(SessionStatus.PENDING)
                .build();
        sessionRepository.save(session);
        return SessionListResponse.from(session, false);
    }

    @Transactional
    public SessionListResponse approveSession(Long userId, Long matchingId, Long sessionId) {
        lmsAccessService.validateMentorAccess(userId, matchingId);
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다: " + sessionId));
        if (!session.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 세션이 아닙니다");
        }
        if (session.getStatus() != SessionStatus.PENDING) {
            throw new InvalidSessionStateException("승인 대기 상태의 세션만 승인할 수 있습니다");
        }
        session.approve();
        // 승인 시 Google Calendar/Meet 링크 생성
        Matching matching = lmsAccessService.validateAccess(userId, matchingId);
        generateAndSaveMeetLink(matching, session);
        return SessionListResponse.from(session, false);
    }

    @Transactional
    public SessionListResponse rejectSession(Long userId, Long matchingId, Long sessionId) {
        lmsAccessService.validateMentorAccess(userId, matchingId);
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다: " + sessionId));
        if (!session.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 세션이 아닙니다");
        }
        if (session.getStatus() != SessionStatus.PENDING) {
            throw new InvalidSessionStateException("승인 대기 상태의 세션만 거절할 수 있습니다");
        }
        session.cancel();
        unbookMatchingSlot(session);
        return SessionListResponse.from(session, false);
    }

    // ─── Change Requests ───

    public List<ChangeRequestResponse> getChangeRequests(Long userId, Long matchingId, Long sessionId) {
        lmsAccessService.validateAccess(userId, matchingId);
        return changeRequestRepository.findBySessionIdOrderByCreatedAtDesc(sessionId)
                .stream().map(ChangeRequestResponse::from).toList();
    }

    @Transactional
    public ChangeRequestResponse createChangeRequest(Long userId, Long matchingId, ChangeRequestCreateRequest request) {
        lmsAccessService.validateAccess(userId, matchingId);
        MentoringSession session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다: " + request.getSessionId()));
        if (!session.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 세션이 아닙니다");
        }
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new InvalidSessionStateException("예정 상태의 세션만 변경 요청할 수 있습니다");
        }
        if (hasStarted(session)) {
            throw new InvalidSessionStateException("이미 시작된 세션은 변경 요청할 수 없습니다");
        }
        if (changeRequestRepository.existsBySessionIdAndStatus(session.getId(), ChangeRequestStatus.PENDING)) {
            throw new SessionAlreadyExistsException("이미 대기 중인 변경 요청이 있습니다");
        }
        SessionChangeRequest cr = SessionChangeRequest.builder()
                .sessionId(session.getId())
                .requesterId(userId)
                .newDate(request.getNewDate())
                .newStartTime(request.getNewStartTime())
                .newEndTime(request.getNewEndTime())
                .reason(request.getReason())
                .build();
        return ChangeRequestResponse.from(changeRequestRepository.save(cr));
    }

    @Transactional
    public ChangeRequestResponse approveChangeRequest(Long userId, Long matchingId, Long requestId) {
        lmsAccessService.validateAccess(userId, matchingId);
        SessionChangeRequest cr = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ChangeRequestNotFoundException("변경 요청을 찾을 수 없습니다: " + requestId));
        if (cr.getRequesterId().equals(userId)) {
            throw new LmsAccessDeniedException("본인이 요청한 변경은 본인이 승인할 수 없습니다");
        }
        if (cr.getStatus() != ChangeRequestStatus.PENDING) {
            throw new InvalidSessionStateException("대기 상태의 요청만 승인할 수 있습니다");
        }
        MentoringSession session = sessionRepository.findById(cr.getSessionId())
                .orElseThrow(() -> new SessionNotFoundException("세션을 찾을 수 없습니다"));
        if (hasStarted(session)) {
            throw new InvalidSessionStateException("이미 시작된 세션은 변경할 수 없습니다");
        }
        session.updateSchedule(cr.getNewDate(), cr.getNewStartTime(), cr.getNewEndTime());
        cr.approve();

        // 시간 변경 시 Google Calendar 업데이트
        if (session.getCalendarEventId() != null) {
            googleCalendarService.updateEvent(
                    session.getCalendarEventId(),
                    session.getCategory(),
                    session.getSessionDate(),
                    session.getStartTime(),
                    session.getEndTime()
            );
        }

        return ChangeRequestResponse.from(cr);
    }

    @Transactional
    public ChangeRequestResponse rejectChangeRequest(Long userId, Long matchingId, Long requestId) {
        lmsAccessService.validateAccess(userId, matchingId);
        SessionChangeRequest cr = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new ChangeRequestNotFoundException("변경 요청을 찾을 수 없습니다: " + requestId));
        if (cr.getRequesterId().equals(userId)) {
            throw new LmsAccessDeniedException("본인이 요청한 변경은 본인이 거절할 수 없습니다");
        }
        if (cr.getStatus() != ChangeRequestStatus.PENDING) {
            throw new InvalidSessionStateException("대기 상태의 요청만 거절할 수 있습니다");
        }
        cr.reject();
        return ChangeRequestResponse.from(cr);
    }
}
