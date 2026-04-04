package com.devmatch.service;

import com.devmatch.entity.MentorAvailability;
import com.devmatch.entity.MentorProfile;
import com.devmatch.entity.MentorStatus;
import com.devmatch.entity.RecommendedMentor;
import com.devmatch.entity.SurveyResponse;
import com.devmatch.repository.MentorAvailabilityRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.RecommendedMentorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final MentorProfileRepository mentorProfileRepository;
    private final MentorAvailabilityRepository mentorAvailabilityRepository;
    private final RecommendedMentorRepository recommendedMentorRepository;

    /**
     * Rule-based 멘토 추천 알고리즘
     * 설문 조사(SurveyResponse)를 기반으로 최적의 멘토를 6명까지 스코어링하여 저장합니다.
     *
     * 스코어링 가중치:
     * - 기술 스택 일치도: 최대 40점
     * - 피드백/학습 스타일 적합도: 최대 30점
     * - 스케줄 가용 시간 중첩: 최대 30점
     */
    @Transactional
    public List<RecommendedMentor> recommendMentors(SurveyResponse survey) {
        log.info("[Recommendation] 멘토 추천 시작 — surveyId: {}", survey.getId());

        // 1. 승인된(APPROVED) 전체 멘토 풀 조회
        List<MentorProfile> allMentors = mentorProfileRepository.findByStatus(MentorStatus.APPROVED);

        if (allMentors.isEmpty()) {
            log.warn("[Recommendation] 승인된 멘토가 없습니다.");
            return List.of();
        }

        // 2. 각 멘토에 대해 3가지 가중치 스코어링
        List<RecommendedMentor> scoredMentors = allMentors.stream()
                .map(mentor -> calculateScore(survey, mentor))
                .sorted(Comparator.comparingInt(RecommendedMentor::getMatchScore).reversed())
                .limit(6) // 상위 최대 6명
                .collect(Collectors.toList());

        // 3. 추천 결과 DB 저장
        List<RecommendedMentor> saved = recommendedMentorRepository.saveAll(scoredMentors);
        log.info("[Recommendation] {} 명의 멘토 추천 완료", saved.size());
        return saved;
    }

    // ===== 내부 스코어링 로직 =====

    private RecommendedMentor calculateScore(SurveyResponse survey, MentorProfile mentor) {
        int techScore = calculateTechScore(survey, mentor);
        int styleScore = calculateStyleScore(survey, mentor);
        int scheduleScore = calculateScheduleScore(survey, mentor);
        int totalScore = techScore + styleScore + scheduleScore;

        String reason = buildRecommendReason(techScore, styleScore, scheduleScore, mentor);

        return RecommendedMentor.builder()
                .surveyResponse(survey)
                .mentor(mentor.getUser())
                .matchScore(totalScore)
                .recommendReason(reason)
                .isSelected(false)
                .build();
    }

    /**
     * 가중치 1: 기술 스택 일치도 (최대 40점)
     * 멘토의 specialty 목록과 멘티의 techStack 키워드를 교차 비교
     */
    private int calculateTechScore(SurveyResponse survey, MentorProfile mentor) {
        if (mentor.getSpecialty() == null || mentor.getSpecialty().isEmpty()
                || survey.getTechStack() == null || survey.getTechStack().isBlank()) {
            return 5; // 데이터 부족 시 최소 점수
        }

        Set<String> menteeStacks = Arrays.stream(survey.getTechStack().split(","))
                .map(s -> s.trim().toLowerCase())
                .collect(Collectors.toSet());

        long matchCount = mentor.getSpecialty().stream()
                .filter(s -> menteeStacks.contains(s.toLowerCase().trim()))
                .count();

        int totalStacks = menteeStacks.size();
        if (totalStacks == 0) return 5;

        // 일치 비율에 따라 0~40점 사이에서 비례 배분
        double ratio = (double) matchCount / totalStacks;
        return (int) Math.round(ratio * 40);
    }

    /**
     * 가중치 2: 피드백/학습 스타일 적합도 (최대 30점)
     * 멘티의 현재 레벨과 피드백 선호도를 기반으로 경력 적합성 판단
     */
    private int calculateStyleScore(SurveyResponse survey, MentorProfile mentor) {
        int score = 0;

        // 경력이 높을수록 기본 가산점 (최대 15점)
        if (mentor.getCareerYears() >= 7) {
            score += 15;
        } else if (mentor.getCareerYears() >= 3) {
            score += 10;
        } else {
            score += 5;
        }

        // 멘티의 레벨과 멘토 경력의 궁합 (최대 15점)
        String level = survey.getCurrentLevel();
        if (level != null) {
            switch (level.toUpperCase()) {
                case "BEGINNER":
                    // 초보자에게는 중간 경력(3~7년) 멘토가 소통이 잘 됨
                    if (mentor.getCareerYears() >= 3 && mentor.getCareerYears() <= 7) {
                        score += 15;
                    } else {
                        score += 8;
                    }
                    break;
                case "INTERMEDIATE":
                    // 중급자에게는 시니어(5년+) 멘토가 좋음
                    if (mentor.getCareerYears() >= 5) {
                        score += 15;
                    } else {
                        score += 7;
                    }
                    break;
                case "ADVANCED":
                    // 고급자에게는 업계 베테랑(7년+) 멘토가 좋음
                    if (mentor.getCareerYears() >= 7) {
                        score += 15;
                    } else {
                        score += 5;
                    }
                    break;
                default:
                    score += 10;
            }
        } else {
            score += 10;
        }

        return Math.min(score, 30); // 최대 30점
    }

    /**
     * 가중치 3: 스케줄 가용 시간 중첩 확인 (최대 30점)
     * 멘티의 preferredSchedule과 MentorAvailability 테이블을 교차 검증
     *
     * preferredSchedule 형식: "MON:19:00-21:00,WED:20:00-22:00"
     */
    private int calculateScheduleScore(SurveyResponse survey, MentorProfile mentor) {
        if (survey.getPreferredSchedule() == null || survey.getPreferredSchedule().isBlank()) {
            return 15; // 스케줄 미입력 시 중간 점수
        }

        // 멘토의 가용 시간 목록 조회
        List<MentorAvailability> mentorSlots =
                mentorAvailabilityRepository.findByMentorIdAndIsActiveTrue(mentor.getUser().getId());

        if (mentorSlots.isEmpty()) {
            return 0; // 멘토가 가용 시간을 등록하지 않은 경우
        }

        // 멘티의 희망 요일 목록 추출
        Set<String> menteeDays = Arrays.stream(survey.getPreferredSchedule().split(","))
                .map(slot -> slot.split(":")[0].trim().toUpperCase())
                .collect(Collectors.toSet());

        // 멘토의 가용 요일과 겹치는 수 계산
        long overlapCount = mentorSlots.stream()
                .filter(slot -> menteeDays.contains(slot.getDayOfWeek().toUpperCase()))
                .count();

        if (overlapCount == 0) {
            return 0; // 겹치는 요일이 전혀 없음
        } else if (overlapCount >= 2) {
            return 30; // 2개 이상 겹치면 만점
        } else {
            return 15; // 1개만 겹치면 절반
        }
    }

    /**
     * 추천 사유 한 줄 평 생성
     */
    private String buildRecommendReason(int techScore, int styleScore, int scheduleScore, MentorProfile mentor) {
        StringBuilder reason = new StringBuilder();

        if (techScore >= 30) {
            reason.append("기술 스택이 정확히 일치합니다. ");
        } else if (techScore >= 15) {
            reason.append("관련 기술 경험이 풍부합니다. ");
        }

        if (styleScore >= 25) {
            reason.append("학습 수준에 최적화된 멘토링을 제공합니다. ");
        }

        if (scheduleScore >= 20) {
            reason.append("시간대가 잘 맞습니다. ");
        } else if (scheduleScore == 0) {
            reason.append("시간 조율이 필요할 수 있습니다. ");
        }

        if (mentor.getCompany() != null && !mentor.getCompany().isBlank()) {
            reason.append(mentor.getCompany()).append(" 현직자입니다.");
        }

        return reason.toString().trim();
    }
}
