package com.devmatch.service;

import com.devmatch.dto.application.ApplicationRequest;
import com.devmatch.dto.application.ApplicationResponse;
import com.devmatch.entity.*;
import com.devmatch.repository.ApplicationRepository;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final MatchingRepository matchingRepository;
    private final MentorProfileRepository mentorProfileRepository;

    @Transactional
    public ApplicationResponse submitApplication(Long userId, ApplicationRequest request) {
        // 신청자는 인증 주체(JWT)에서만 결정한다 — 요청 본문의 menteeId 는 신뢰하지 않는다 (사칭 방지).
        User mentee = userRepository.findById(userId)
                .orElseThrow(() -> new com.devmatch.exception.UserNotFoundException("Mentee not found with ID: " + userId));

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

    public void createAutoMatching(Application application) {
        if (application.getStatus() == ApplicationStatus.MATCHING_FAILED ||
            application.getStatus() == ApplicationStatus.ACCEPTED) {
            return;
        }

        List<User> eligibleMentors = mentorProfileRepository.findByStatus(MentorStatus.APPROVED).stream()
                .filter(profile -> profile.getCourses() != null
                        && profile.getCourses().stream()
                                .anyMatch(course -> course.getCourseKey().equals(application.getCategory())))
                .map(MentorProfile::getUser)
                .toList();

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
            Matching matching = Matching.builder()
                    .mentee(application.getMentee())
                    .mentor(nextMentor)
                    .category(application.getCategory())
                    .applicationId(application.getId())
                    .status(MatchingStatus.ACCEPTED)
                    .build();
            matchingRepository.save(matching);
            application.completeAutoMatch(nextMentor);
        } else {
            application.markMatchingFailed();
        }
    }

    @Transactional
    public Application confirmPayment(Long userId, Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.devmatch.exception.UserNotFoundException("Application not found with ID: " + applicationId));

        // 본인 신청서만 결제 확정 가능 (IDOR 방지)
        if (!application.getMentee().getId().equals(userId)) {
            throw new com.devmatch.exception.ForbiddenOperationException("본인의 신청서만 결제 확정할 수 있습니다.");
        }

        if (application.getStatus() == ApplicationStatus.ACCEPTED ||
            application.getStatus() == ApplicationStatus.MATCHING_FAILED) {
            return application;
        }

        if (application.getStatus() != ApplicationStatus.PAYMENT_COMPLETED) {
            application.markPaid();
        }
        createAutoMatching(application);
        return application;
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
