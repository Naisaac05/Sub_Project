package com.devmatch.service;

import com.devmatch.entity.*;
import com.devmatch.exception.ForbiddenOperationException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.exception.MentorProfileNotFoundException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MentorSwapService {

    private final UserRepository userRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final MatchingRepository matchingRepository;
    private final AdminAuditLogService adminAuditLogService;

    @Transactional
    public void swap(Long adminId, Long menteeUserId, Long newMentorUserId, String reason) {
        User mentee = userRepository.findById(menteeUserId)
                .orElseThrow(() -> new UserNotFoundException("멘티를 찾을 수 없습니다: " + menteeUserId));
        if (mentee.getStatus() != UserStatus.ACTIVE) {
            throw new ForbiddenOperationException("ACTIVE 상태의 멘티만 멘토 교체가 가능합니다.");
        }

        MentorProfile newMentorProfile = mentorProfileRepository.findByUserId(newMentorUserId)
                .orElseThrow(() -> new MentorProfileNotFoundException("멘토 프로필을 찾을 수 없습니다"));
        if (newMentorProfile.getStatus() != MentorStatus.APPROVED) {
            throw new ForbiddenOperationException("승인된 멘토만 교체 대상이 될 수 있습니다.");
        }
        if (newMentorProfile.getUser().getId().equals(menteeUserId)) {
            throw new ForbiddenOperationException("자기 자신을 멘토로 지정할 수 없습니다.");
        }

        Matching old = matchingRepository.findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
                        menteeUserId, List.of(MatchingStatus.PENDING, MatchingStatus.ACCEPTED, MatchingStatus.TRIAL))
                .orElseThrow(() -> new ForbiddenOperationException("교체할 활성 매칭이 없습니다."));

        if (old.getMentor().getId().equals(newMentorUserId)) {
            throw new ForbiddenOperationException("동일한 멘토로는 교체할 수 없습니다.");
        }

        Long oldMentorUserId = old.getMentor().getId();
        Long oldMatchingId = old.getId();

        old.swap();

        Matching neo = Matching.builder()
                .mentee(mentee)
                .mentor(newMentorProfile.getUser())
                .category(old.getCategory())
                .applicationId(old.getApplicationId())
                .testResult(old.getTestResult())
                .status(MatchingStatus.ACCEPTED)
                .build();
        matchingRepository.save(neo);

        adminAuditLogService.record(adminId, AdminActionType.USER_MENTOR_SWAP,
                "USER", menteeUserId, reason,
                Map.of("oldMatchingId", oldMatchingId,
                       "oldMentorUserId", oldMentorUserId,
                       "newMentorUserId", newMentorUserId));
    }
}
