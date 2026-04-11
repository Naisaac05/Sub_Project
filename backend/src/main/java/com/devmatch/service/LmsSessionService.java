package com.devmatch.service;

import com.devmatch.dto.lms.*;
import com.devmatch.entity.*;
import com.devmatch.exception.*;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LmsSessionService {

    private final LmsAccessService lmsAccessService;
    private final MentoringSessionRepository sessionRepository;
    private final MentorTimeSlotRepository timeSlotRepository;
    private final SessionChangeRequestRepository changeRequestRepository;

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
        if (session.getStatus() != SessionStatus.SCHEDULED) {
            throw new InvalidSessionStateException("예정 상태의 세션만 취소할 수 있습니다");
        }
        session.cancel();
        return SessionListResponse.from(session, false);
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
                .findByMatchingIdAndSlotDateAndIsBookedFalseOrderByStartTimeAsc(matchingId, date)
                .stream().map(TimeSlotResponse::from).toList();
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
        lmsAccessService.validateMentorAccess(userId, matchingId);
        MentorTimeSlot slot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new TimeSlotNotFoundException("슬롯을 찾을 수 없습니다: " + slotId));
        if (!slot.getMatchingId().equals(matchingId)) {
            throw new LmsAccessDeniedException("해당 매칭의 슬롯이 아닙니다");
        }
        if (slot.getIsBooked()) {
            throw new InvalidSessionStateException("이미 예약된 슬롯은 삭제할 수 없습니다");
        }
        timeSlotRepository.delete(slot);
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
                .build();
        sessionRepository.save(session);
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
        session.updateSchedule(cr.getNewDate(), cr.getNewStartTime(), cr.getNewEndTime());
        cr.approve();
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
