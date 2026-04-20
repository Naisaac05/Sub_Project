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

        return convertToResponse(application);
    }

    public void assignNextAvailableMentor(Application application) {
        if (application.getStatus() == ApplicationStatus.MATCHING_FAILED ||
            application.getStatus() == ApplicationStatus.ACCEPTED) {
            return;
        }

        Set<Long> rejectedMentorIds = application.getRejectedMentors();

        List<User> eligibleMentors = userRepository.findByRole(Role.MENTOR).stream()
                .filter(u -> !rejectedMentorIds.contains(u.getId()))
                .collect(Collectors.toList());

        if (eligibleMentors.isEmpty()) {
            application.markMatchingFailed();
            return;
        }

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

        application.markPaid();
        assignNextAvailableMentor(application);
        return application;
    }

    @Transactional
    public ApplicationResponse approveApplication(Long applicationId, Long mentorId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid application ID"));

        if (application.getAssignedMentor() == null || !application.getAssignedMentor().getId().equals(mentorId)) {
            throw new IllegalStateException("해당 신청서는 현재 멘토가 승인할 수 없는 상태입니다.");
        }

        Matching matching = Matching.builder()
                .mentee(application.getMentee())
                .mentor(application.getAssignedMentor())
                .category(application.getCategory())
                .applicationId(application.getId())
                .status(MatchingStatus.ACCEPTED)
                .build();
        matchingRepository.save(matching);

        application.acceptAutoMatch();
        return convertToResponse(application);
    }

    @Transactional
    public ApplicationResponse rejectApplication(Long applicationId, Long mentorId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid application ID"));

        if (application.getAssignedMentor() == null || !application.getAssignedMentor().getId().equals(mentorId)) {
            throw new IllegalStateException("해당 신청서는 현재 멘토가 거절할 수 없는 상태입니다.");
        }

        application.rejectByCurrentMentor();
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
