package com.devmatch.service;

import com.devmatch.entity.Matching;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.LmsAccessDeniedException;
import com.devmatch.exception.MatchingNotFoundException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LmsAccessService {
    private final MatchingRepository matchingRepository;
    private final PaymentRepository paymentRepository;

    public Matching validateAccess(Long userId, Long matchingId) {
        Matching matching = matchingRepository.findById(matchingId)
                .orElseThrow(() -> new MatchingNotFoundException("매칭을 찾을 수 없습니다: " + matchingId));
        boolean isParticipant = matching.getMentee().getId().equals(userId)
                || matching.getMentor().getId().equals(userId);
        if (!isParticipant) {
            throw new LmsAccessDeniedException("해당 매칭에 대한 접근 권한이 없습니다");
        }
        MatchingStatus status = matching.getStatus();
        if (status != MatchingStatus.TRIAL && status != MatchingStatus.ACCEPTED) {
            throw new LmsAccessDeniedException("활성 상태의 매칭만 LMS에 접근할 수 있습니다. 현재 상태: " + status);
        }
        // TRIAL: 7일 무료 체험, 결제 없이 접근 가능
        // ACCEPTED: 정식 수강, 결제 확인 필수 (멘토는 제외 — 멘토는 결제 주체가 아님)
        if (status == MatchingStatus.ACCEPTED && matching.getMentee().getId().equals(userId)) {
            Payment payment = paymentRepository.findByMatchingId(matchingId)
                    .orElseThrow(() -> new LmsAccessDeniedException("LMS 이용을 위한 결제가 필요합니다"));
            if (payment.getStatus() != PaymentStatus.CONFIRMED) {
                throw new LmsAccessDeniedException("결제가 완료되지 않았습니다. 현재 결제 상태: " + payment.getStatus());
            }
        }
        return matching;
    }

    public Matching validateMentorAccess(Long userId, Long matchingId) {
        Matching matching = validateAccess(userId, matchingId);
        if (!matching.getMentor().getId().equals(userId)) {
            throw new LmsAccessDeniedException("멘토만 수행할 수 있는 작업입니다");
        }
        return matching;
    }

    public Matching validateMenteeAccess(Long userId, Long matchingId) {
        Matching matching = validateAccess(userId, matchingId);
        if (!matching.getMentee().getId().equals(userId)) {
            throw new LmsAccessDeniedException("멘티만 수행할 수 있는 작업입니다");
        }
        return matching;
    }
}
