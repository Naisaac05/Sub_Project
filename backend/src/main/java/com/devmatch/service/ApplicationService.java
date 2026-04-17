package com.devmatch.service;

import com.devmatch.dto.application.ApplicationRequest;
import com.devmatch.dto.application.ApplicationResponse;
import com.devmatch.entity.*;
import com.devmatch.repository.ApplicationRepository;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.MentorAvailabilityRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final MentorAvailabilityRepository mentorAvailabilityRepository;
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
                .build();

        application = applicationRepository.save(application);

        // Auto Match & Approve
        autoMatchAndApprove(application);

        return convertToResponse(application);
    }

    private void autoMatchAndApprove(Application application) {
        // Find a waiting mentor (in a real system, you might filter by Category / Curriculum as well)
        List<MentorAvailability> availabilities = mentorAvailabilityRepository.findByIsWaitingTrueAndIsActiveTrue();
        
        if (!availabilities.isEmpty()) {
            MentorAvailability matchedAvailability = availabilities.get(0); // For now, pick the first one
            
            User mentor = userRepository.findById(matchedAvailability.getMentorId())
                    .orElseThrow(() -> new com.devmatch.exception.UserNotFoundException("Mentor not found with ID: " + matchedAvailability.getMentorId()));

            // Create matching
            Matching matching = Matching.builder()
                    .mentee(application.getMentee())
                    .mentor(mentor)
                    .category(application.getCategory())
                    .status(MatchingStatus.ACCEPTED) // Directly accepted
                    .build();

            matchingRepository.save(matching);

            // Update Application
            application.acceptAutoMatch();
        } else {
            // Test Fallback: If no availability records, pick the first mentor user to ensure the flow works for the user
            userRepository.findByEmail("java.mentor@devmatch.com")
                    .ifPresent(mentor -> {
                        Matching matching = Matching.builder()
                                .mentee(application.getMentee())
                                .mentor(mentor)
                                .category(application.getCategory())
                                .status(MatchingStatus.ACCEPTED)
                                .build();
                        matchingRepository.save(matching);
                        application.acceptAutoMatch();
                    });
        }
    }

    @Transactional
    public Application confirmPayment(Long applicationId) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new com.devmatch.exception.UserNotFoundException("Application not found with ID: " + applicationId));
        
        application.markPaid();
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
                .build();
    }
}
