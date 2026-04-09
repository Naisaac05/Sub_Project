package com.devmatch.service;

import com.devmatch.entity.Matching;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.exception.LmsAccessDeniedException;
import com.devmatch.exception.MatchingNotFoundException;
import com.devmatch.repository.MatchingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LmsAccessService {
    private final MatchingRepository matchingRepository;

    public Matching validateAccess(Long userId, Long matchingId) {
        Matching matching = matchingRepository.findById(matchingId)
                .orElseThrow(() -> new MatchingNotFoundException("매칭을 찾을 수 없습니다: " + matchingId));
        boolean isParticipant = matching.getMentee().getId().equals(userId)
                || matching.getMentor().getId().equals(userId);
        if (!isParticipant) {
            throw new LmsAccessDeniedException("해당 매칭에 대한 접근 권한이 없습니다");
        }
        boolean isActiveMatching = matching.getStatus() == MatchingStatus.TRIAL
                || matching.getStatus() == MatchingStatus.ACCEPTED;
        if (!isActiveMatching) {
            throw new LmsAccessDeniedException("활성 상태의 매칭만 LMS에 접근할 수 있습니다. 현재 상태: " + matching.getStatus());
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
