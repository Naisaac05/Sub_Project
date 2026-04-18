package com.devmatch.service;

import com.devmatch.dto.application.ApplicationRequest;
import com.devmatch.dto.application.ApplicationResponse;
import com.devmatch.entity.*;
import com.devmatch.repository.ApplicationRepository;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final MatchingRepository matchingRepository;

    @Transactional
    public ApplicationResponse submitApplication(ApplicationRequest request) {
        User mentee = userRepository.findById(request.getMenteeId())
                .orElseThrow(() -> new com.devmatch.exception.UserNotFoundException("Mentee not found with ID: " + request.getMenteeId()));

        Application application = Application.builder()
                .mentee(mentee)
                .currentLevel(request.getCurrentLevel())
                .targetTechStack(request.getTargetTechStack())
                .careerGoal(request.getCareerGoal())
                .category(request.getCategory())
                .courseType(request.getCourseType())
                .desiredMonths(request.getDesiredMonths())
                .languages(request.getLanguages())
                .platforms(request.getPlatforms())
                .isCsMajor(request.getIsCsMajor())
                .learningPaths(request.getLearningPaths())
                .careerYears(request.getCareerYears())
                .githubUrl(request.getGithubUrl())
                .projectCount(request.getProjectCount())
                .projectDescription(request.getProjectDescription())
                .weekdayStudyHours(request.getWeekdayStudyHours())
                .weekendStudyHours(request.getWeekendStudyHours())
                .goal(request.getGoal())
                .personality(request.getPersonality())
                .selfIntroduction(request.getSelfIntroduction())
                .referralSources(request.getReferralSources())
                .referralCode(request.getReferralCode())
                .termsAgreed(request.getTermsAgreed())
                .status(ApplicationStatus.SUBMITTED)
                .submittedAt(LocalDateTime.now())
                .autoMatched("IMMEDIATE".equalsIgnoreCase(request.getCourseType()))
                .build();

        application = applicationRepository.save(application);

        // 이전: 여기서 autoMatchAndApprove() 호출 (자동 매칭)
        // 현재: 결제 후(confirmPayment) 멘토 배정을 시도함. 일단 SUBMITTED 상태로 보존.

        return convertToResponse(application);
    }

    /**
     * 멘티가 적은 순(우선) -> 가입 순(ID) 1순위 멘토를 찾아 할당합니다.
     */
    public void assignNextAvailableMentor(Application application) {
        // 이미 멘토들이 모두 거절한 상태거나 완료된 상태면 스킵
        if (application.getStatus() == ApplicationStatus.MATCHING_FAILED ||
            application.getStatus() == ApplicationStatus.ACCEPTED) {
            return;
        }

        Set<Long> rejectedMentorIds = application.getRejectedMentors();

        // 모든 멘토 목록을 가져와서 (Role = MENTOR) 거절 명단에 없는 멘토만 필터링
        List<User> eligibleMentors = userRepository.findByRole(Role.MENTOR).stream()
                .filter(u -> !rejectedMentorIds.contains(u.getId()))
                .collect(Collectors.toList());

        if (eligibleMentors.isEmpty()) {
            application.markMatchingFailed();
            return;
        }

        // 우선순위 정렬: 1) 진행중(ACCEPTED/TRIAL) 멘티 수 ASC, 2) User ID ASC
        User nextMentor = eligibleMentors.stream()
                .min(Comparator.comparingInt((User m) -> matchingRepository.countByMentorIdAndStatusIn(
                        m.getId(), Arrays.asList(MatchingStatus.ACCEPTED, MatchingStatus.TRIAL)))
                        .thenComparing(User::getId))
                .orElse(null);

        if (nextMentor != null) {
            application.assignMentor(nextMentor);
        } else {
            application.markMatchingFailed();
        }
    }

    @Transactional
    public Application confirmPayment(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.devmatch.exception.UserNotFoundException("Application not found with ID: " + applicationId));
        
        // PAYMENT_COMPLETED 지만, 바로 PENDING_MENTOR_APPROVAL 로 넘어감
        application.markPaid();
        
        // 결제 완료 시 시스템이 순서대로 멘토 한 명을 자동 할당
        assignNextAvailableMentor(application);
        return application;
    }

    @Transactional
    public ApplicationResponse approveApplication(Long applicationId, Long mentorId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid application ID"));

        // 자신이 할당받은 신청서인지 확인
        if (application.getAssignedMentor() == null || !application.getAssignedMentor().getId().equals(mentorId)) {
            throw new IllegalStateException("해당 신청인에 대한 할당 권한이 없습니다.");
        }

        // Matching 생성
        Matching matching = Matching.builder()
                .mentee(application.getMentee())
                .mentor(application.getAssignedMentor())
                .category(application.getCategory())
                .applicationId(application.getId())
                .status(MatchingStatus.ACCEPTED)
                .build();
        matchingRepository.save(matching);

        // Application 상태 업데이트
        application.acceptAutoMatch();
        return convertToResponse(application);
    }

    @Transactional
    public ApplicationResponse rejectApplication(Long applicationId, Long mentorId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid application ID"));

        if (application.getAssignedMentor() == null || !application.getAssignedMentor().getId().equals(mentorId)) {
            throw new IllegalStateException("해당 신청인에 대한 할당 권한이 없습니다.");
        }

        // 현재 멘토 거절 처리 및 명단 등록
        application.rejectByCurrentMentor();

        // 다음 멘토 재할당 검색
        assignNextAvailableMentor(application);
        return convertToResponse(application);
    }

    public List<ApplicationResponse> getMyAssignments(Long mentorId) {
        List<Application> assignments = applicationRepository.findByAssignedMentorIdAndStatusOrderByCreatedAtAsc(
                mentorId, ApplicationStatus.PENDING_MENTOR_APPROVAL);
        return assignments.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    public ApplicationResponse convertToResponse(Application application) {
        return ApplicationResponse.builder()
                .id(application.getId())
                .menteeId(application.getMentee().getId())
                .currentLevel(application.getCurrentLevel())
                .targetTechStack(application.getTargetTechStack())
                .careerGoal(application.getCareerGoal())
                .category(application.getCategory())
                .courseType(application.getCourseType())
                .desiredMonths(application.getDesiredMonths())
                .languages(application.getLanguages())
                .platforms(application.getPlatforms())
                .isCsMajor(application.getIsCsMajor())
                .learningPaths(application.getLearningPaths())
                .careerYears(application.getCareerYears())
                .githubUrl(application.getGithubUrl())
                .projectCount(application.getProjectCount())
                .projectDescription(application.getProjectDescription())
                .weekdayStudyHours(application.getWeekdayStudyHours())
                .weekendStudyHours(application.getWeekendStudyHours())
                .goal(application.getGoal())
                .personality(application.getPersonality())
                .selfIntroduction(application.getSelfIntroduction())
                .referralSources(application.getReferralSources())
                .referralCode(application.getReferralCode())
                .termsAgreed(application.getTermsAgreed())
                .status(application.getStatus())
                .autoMatched(application.getAutoMatched())
                .submittedAt(application.getSubmittedAt())
                .assignedMentorId(application.getAssignedMentor() != null ? application.getAssignedMentor().getId() : null)
                .build();
    }
}
