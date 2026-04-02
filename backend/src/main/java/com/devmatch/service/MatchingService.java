package com.devmatch.service;

import com.devmatch.dto.matching.*;
import com.devmatch.entity.*;
import com.devmatch.exception.*;
import com.devmatch.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MatchingService {

    private final MatchingRepository matchingRepository;
    private final MentorProfileRepository mentorProfileRepository;
    private final UserRepository userRepository;
    private final TestResultRepository testResultRepository;

    public List<MentorRecommendResponse> recommendMentors(Long userId, String category) {
        // APPROVED 멘토 중 해당 분야 전문가 필터링
        List<MentorProfile> approvedMentors = mentorProfileRepository.findByStatus(MentorStatus.APPROVED)
                .stream()
                .filter(profile -> profile.getSpecialty() != null
                        && profile.getSpecialty().contains(category))
                .collect(Collectors.toList());

        // 사용자의 해당 분야 최근 테스트 결과 조회
        TestResult latestResult = testResultRepository
                .findTopByUserIdAndTest_CategoryOrderBySubmittedAtDesc(userId, category)
                .orElse(null);

        return approvedMentors.stream()
                .map(profile -> {
                    int matchScore = calculateMatchScore(profile, latestResult);
                    return MentorRecommendResponse.of(profile, matchScore);
                })
                .sorted(Comparator.comparing(MentorRecommendResponse::getMatchScore).reversed())
                .collect(Collectors.toList());
    }

    @Transactional
    public MatchingResponse requestMatching(Long menteeId, MatchingRequest request) {
        // 중복 PENDING 신청 확인
        if (matchingRepository.existsByMenteeIdAndMentorIdAndStatus(
                menteeId, request.getMentorId(), MatchingStatus.PENDING)) {
            throw new DuplicateMatchingException("이미 해당 멘토에게 대기 중인 매칭 신청이 있습니다");
        }

        User mentee = userRepository.findById(menteeId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        User mentor = userRepository.findById(request.getMentorId())
                .orElseThrow(() -> new UserNotFoundException("멘토를 찾을 수 없습니다"));

        // 멘토가 APPROVED 상태인지 확인
        MentorProfile mentorProfile = mentorProfileRepository.findByUserId(mentor.getId())
                .orElseThrow(() -> new UserNotFoundException("멘토 프로필을 찾을 수 없습니다"));
        if (mentorProfile.getStatus() != MentorStatus.APPROVED) {
            throw new UnauthorizedMatchingException("승인된 멘토에게만 매칭 신청이 가능합니다");
        }

        // 테스트 결과 연결 (선택)
        TestResult testResult = null;
        if (request.getTestResultId() != null) {
            testResult = testResultRepository.findById(request.getTestResultId())
                    .orElse(null);
        }

        Matching matching = Matching.builder()
                .mentee(mentee)
                .mentor(mentor)
                .testResult(testResult)
                .category(request.getCategory())
                .message(request.getMessage())
                .build();

        matching = matchingRepository.save(matching);
        return MatchingResponse.from(matching);
    }

    @Transactional
    public MatchingResponse acceptMatching(Long mentorId, Long matchingId, MatchingAcceptRequest request) {
        Matching matching = matchingRepository.findById(matchingId)
                .orElseThrow(() -> new MatchingNotFoundException("매칭을 찾을 수 없습니다"));

        // 본인 매칭인지 확인
        if (!matching.getMentor().getId().equals(mentorId)) {
            throw new UnauthorizedMatchingException("본인에게 온 매칭 요청만 처리할 수 있습니다");
        }

        // PENDING 상태인지 확인
        if (matching.getStatus() != MatchingStatus.PENDING) {
            throw new UnauthorizedMatchingException("대기 중인 매칭만 처리할 수 있습니다");
        }

        if (Boolean.TRUE.equals(request.getAccepted())) {
            matching.accept();
        } else {
            matching.reject(request.getRejectedReason());
        }

        return MatchingResponse.from(matching);
    }

    public List<MatchingResponse> getMyMatchingsAsMentee(Long userId) {
        return matchingRepository.findByMenteeIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(MatchingResponse::from)
                .collect(Collectors.toList());
    }

    public List<MatchingResponse> getMyMatchingsAsMentor(Long userId) {
        return matchingRepository.findByMentorIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(MatchingResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 매칭 적합도 점수 계산 (0~100)
     * - 전문 분야 일치: +40점
     * - 경력 기반 가산: 최대 +30점
     * - 테스트 점수 기반 적합도: 최대 +30점
     */
    private int calculateMatchScore(MentorProfile mentor, TestResult testResult) {
        int score = 0;

        // 전문 분야 일치 (이미 필터링되었으므로 +40)
        score += 40;

        // 경력 기반 가산 (1년당 3점, 최대 30점)
        int careerScore = Math.min(mentor.getCareerYears() * 3, 30);
        score += careerScore;

        // 테스트 점수 기반 적합도
        if (testResult != null) {
            // 테스트 점수가 높을수록 더 경력 많은 멘토와 매칭 → 점수 반영
            int testScore = Math.min(testResult.getTotalScore() * 30 / 100, 30);
            score += testScore;
        }

        return Math.min(score, 100);
    }
}
