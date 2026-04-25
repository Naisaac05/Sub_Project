package com.devmatch.service;

import com.devmatch.dto.menteechange.MentorChangeRequestResponse;
import com.devmatch.entity.Matching;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.MentorChangeRequest;
import com.devmatch.entity.MentorChangeRequestStatus;
import com.devmatch.exception.DuplicatePendingMentorChangeRequestException;
import com.devmatch.exception.ForbiddenOperationException;
import com.devmatch.exception.MentorChangeRequestNotFoundException;
import com.devmatch.exception.NoActiveMatchingException;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MentorChangeRequestService {

    private final MatchingRepository matchingRepository;
    private final MentorChangeRequestRepository requestRepository;

    @Transactional
    public MentorChangeRequestResponse submit(Long menteeUserId, String reason) {
        Matching active = matchingRepository
                .findFirstByMenteeIdAndStatusInOrderByCreatedAtDesc(
                        menteeUserId,
                        List.of(MatchingStatus.ACCEPTED, MatchingStatus.TRIAL))
                .orElseThrow(() -> new NoActiveMatchingException(
                        "활성 매칭이 없어 멘토 교체를 신청할 수 없습니다"));

        if (requestRepository.existsByMenteeIdAndStatus(
                menteeUserId, MentorChangeRequestStatus.PENDING)) {
            throw new DuplicatePendingMentorChangeRequestException(
                    "이미 심사 중인 멘토 교체 신청이 있습니다");
        }

        MentorChangeRequest saved = requestRepository.save(
                MentorChangeRequest.builder()
                        .menteeId(menteeUserId)
                        .currentMatchingId(active.getId())
                        .currentMentorId(active.getMentor().getId())
                        .reason(reason)
                        .status(MentorChangeRequestStatus.PENDING)
                        .build());

        return MentorChangeRequestResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public MentorChangeRequestResponse getLatest(Long menteeUserId) {
        return requestRepository.findFirstByMenteeIdOrderByCreatedAtDesc(menteeUserId)
                .map(MentorChangeRequestResponse::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public MentorChangeRequestResponse getOwn(Long menteeUserId, Long requestId) {
        MentorChangeRequest r = requestRepository.findById(requestId)
                .orElseThrow(() -> new MentorChangeRequestNotFoundException(
                        "신청을 찾을 수 없습니다: " + requestId));
        if (!r.getMenteeId().equals(menteeUserId)) {
            throw new ForbiddenOperationException("본인 신청만 조회할 수 있습니다");
        }
        return MentorChangeRequestResponse.from(r);
    }

    @Transactional
    public void cancel(Long menteeUserId, Long requestId) {
        MentorChangeRequest r = requestRepository.findById(requestId)
                .orElseThrow(() -> new MentorChangeRequestNotFoundException(
                        "신청을 찾을 수 없습니다: " + requestId));
        if (!r.getMenteeId().equals(menteeUserId)) {
            throw new ForbiddenOperationException("본인 신청만 취소할 수 있습니다");
        }
        r.cancel();
    }
}
