package com.devmatch.service;

import com.devmatch.dto.admin.menteechange.*;
import com.devmatch.entity.*;
import com.devmatch.exception.MentorChangeRequestNotFoundException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorChangeRequestRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AdminMentorChangeRequestService {

    private final MentorChangeRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final MatchingRepository matchingRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MentorSwapService mentorSwapService;
    private final AdminAuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<AdminMentorChangeListItemResponse> list(
            MentorChangeRequestStatus status, Pageable pageable) {
        Page<MentorChangeRequest> page = (status == null)
                ? requestRepository.findAll(pageable)
                : requestRepository.findByStatus(status, pageable);
        return page.map(this::toListItem);
    }

    @Transactional(readOnly = true)
    public AdminMentorChangeDetailResponse get(Long requestId) {
        MentorChangeRequest r = findRequiring(requestId);
        return toDetail(r);
    }

    @Transactional(readOnly = true)
    public Page<CandidateMentorResponse> listCandidateMentors(
            Long requestId, String keyword, Pageable pageable) {
        MentorChangeRequest r = findRequiring(requestId);
        Matching m = matchingRepository.findById(r.getCurrentMatchingId())
                .orElseThrow(() -> new IllegalStateException(
                        "매칭이 존재하지 않습니다: " + r.getCurrentMatchingId()));
        String kw = (keyword == null) ? "" : keyword.trim();
        Page<MentorProfile> profiles = mentorProfileRepository.findApprovedByCategoryAndKeyword(
                m.getCategory(), r.getCurrentMentorId(), kw, pageable);
        return profiles.map(p -> {
            int active = matchingRepository.countByMentorIdAndStatusIn(
                    p.getUser().getId(),
                    List.of(MatchingStatus.ACCEPTED, MatchingStatus.TRIAL));
            return new CandidateMentorResponse(
                    p.getUser().getId(),
                    p.getUser().getName(),
                    p.getUser().getEmail(),
                    active);
        });
    }

    @Transactional
    public AdminMentorChangeDetailResponse approve(
            Long adminId, Long requestId, AdminMentorChangeApproveRequest req) {
        MentorChangeRequest r = findRequiring(requestId);
        if (r.getStatus() != MentorChangeRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 상태에서만 처리할 수 있습니다 (현재: " + r.getStatus() + ")");
        }
        Long oldMentorId = r.getCurrentMentorId();
        mentorSwapService.swap(adminId, r.getMenteeId(), req.newMentorUserId(), r.getReason());
        r.approve(adminId, req.newMentorUserId());
        auditLogService.record(adminId, AdminActionType.MENTOR_CHANGE_APPROVE,
                "MENTOR_CHANGE_REQUEST", r.getId(), r.getReason(),
                Map.of("newMentorUserId", req.newMentorUserId(), "oldMentorUserId", oldMentorId));
        return toDetail(r);
    }

    @Transactional
    public AdminMentorChangeDetailResponse reject(
            Long adminId, Long requestId, AdminMentorChangeRejectRequest req) {
        MentorChangeRequest r = findRequiring(requestId);
        String menteeReason = r.getReason();
        r.reject(adminId, req.rejectReason());
        auditLogService.record(adminId, AdminActionType.MENTOR_CHANGE_REJECT,
                "MENTOR_CHANGE_REQUEST", r.getId(), req.rejectReason(),
                Map.of("menteeReason", menteeReason));
        return toDetail(r);
    }

    private MentorChangeRequest findRequiring(Long requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new MentorChangeRequestNotFoundException(
                        "신청을 찾을 수 없습니다: " + requestId));
    }

    private AdminMentorChangeListItemResponse toListItem(MentorChangeRequest r) {
        Optional<User> mentee = userRepository.findById(r.getMenteeId());
        Optional<User> mentor = userRepository.findById(r.getCurrentMentorId());
        String preview = r.getReason() == null ? ""
                : r.getReason().length() > 40 ? r.getReason().substring(0, 40) + "…" : r.getReason();
        return new AdminMentorChangeListItemResponse(
                r.getId(),
                r.getMenteeId(),
                mentee.map(User::getName).orElse("(삭제됨)"),
                mentee.map(User::getEmail).orElse(null),
                r.getCurrentMentorId(),
                mentor.map(User::getName).orElse("(삭제됨)"),
                preview,
                r.getStatus(),
                r.getCreatedAt(),
                r.getRespondedAt());
    }

    private AdminMentorChangeDetailResponse toDetail(MentorChangeRequest r) {
        Optional<User> mentee = userRepository.findById(r.getMenteeId());
        Optional<User> mentor = userRepository.findById(r.getCurrentMentorId());
        Optional<User> newMentor = (r.getNewMentorId() == null)
                ? Optional.empty() : userRepository.findById(r.getNewMentorId());
        Optional<Matching> matching = matchingRepository.findById(r.getCurrentMatchingId());
        return new AdminMentorChangeDetailResponse(
                r.getId(),
                r.getMenteeId(),
                mentee.map(User::getName).orElse("(삭제됨)"),
                mentee.map(User::getEmail).orElse(null),
                r.getCurrentMatchingId(),
                matching.map(Matching::getCategory).orElse(null),
                r.getCurrentMentorId(),
                mentor.map(User::getName).orElse("(삭제됨)"),
                r.getReason(),
                r.getStatus(),
                r.getNewMentorId(),
                newMentor.map(User::getName).orElse(null),
                r.getRejectReason(),
                r.getDecidedByAdminId(),
                r.getCreatedAt(),
                r.getRespondedAt());
    }
}
