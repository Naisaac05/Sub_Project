package com.devmatch.service;

import com.devmatch.dto.mentor.MentorApplyRequest;
import com.devmatch.dto.mentor.MentorProfileResponse;
import com.devmatch.entity.MentorProfile;
import com.devmatch.entity.MentorProfileHistory;
import com.devmatch.entity.MentorStatus;
import com.devmatch.entity.MentoringCourse;
import com.devmatch.entity.User;
import com.devmatch.exception.AlreadyAppliedException;
import com.devmatch.exception.CourseNotFoundException;
import com.devmatch.exception.UserNotFoundException;
import com.devmatch.repository.MentorProfileHistoryRepository;
import com.devmatch.repository.MentorProfileRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MentorService {

    private final MentorProfileRepository mentorProfileRepository;
    private final UserRepository userRepository;
    private final CourseService courseService;
    private final MentorProfileHistoryRepository historyRepository;

    @Transactional
    public MentorProfileResponse apply(Long userId, MentorApplyRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        Optional<MentorProfile> existingOpt = mentorProfileRepository.findByUserId(userId);
        if (existingOpt.isPresent()) {
            MentorStatus currentStatus = existingOpt.get().getStatus();
            if (currentStatus == MentorStatus.PENDING) {
                throw new AlreadyAppliedException("이미 심사 중인 신청이 있습니다");
            }
            if (currentStatus == MentorStatus.APPROVED) {
                throw new AlreadyAppliedException("이미 승인된 멘토입니다");
            }
        }

        List<MentoringCourse> courses = courseService.findActiveByKeys(request.getCourseKeys());
        if (courses.size() != request.getCourseKeys().size()) {
            throw new CourseNotFoundException("유효하지 않거나 비활성화된 코스가 포함되어 있습니다");
        }

        MentorProfile profile;

        if (existingOpt.isPresent()) {
            MentorProfile existing = existingOpt.get();
            existing.updateFromRequest(
                    new HashSet<>(courses),
                    request.getTechStack(),
                    request.getCareerYears(),
                    request.getCompany(),
                    request.getJobTitle(),
                    request.getPortfolioUrl(),
                    request.getEducation(),
                    request.getCertifications(),
                    request.getPreferredMenteeLevel(),
                    request.getBio()
            );
            profile = existing;
        } else {
            profile = MentorProfile.builder()
                    .user(user)
                    .courses(new HashSet<>(courses))
                    .techStack(request.getTechStack())
                    .careerYears(request.getCareerYears())
                    .company(request.getCompany())
                    .jobTitle(request.getJobTitle())
                    .portfolioUrl(request.getPortfolioUrl())
                    .education(request.getEducation())
                    .certifications(request.getCertifications())
                    .preferredMenteeLevel(request.getPreferredMenteeLevel())
                    .bio(request.getBio())
                    .status(MentorStatus.PENDING)
                    .build();
            profile = mentorProfileRepository.save(profile);
        }

        historyRepository.save(MentorProfileHistory.builder()
                .userId(userId)
                .courseKeys(courses.stream().map(MentoringCourse::getCourseKey).toList())
                .techStack(request.getTechStack())
                .careerYears(request.getCareerYears())
                .company(request.getCompany())
                .jobTitle(request.getJobTitle())
                .portfolioUrl(request.getPortfolioUrl())
                .education(request.getEducation())
                .certifications(request.getCertifications())
                .preferredMenteeLevel(request.getPreferredMenteeLevel())
                .bio(request.getBio())
                .status(MentorStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build());

        return MentorProfileResponse.from(profile, null);
    }

    public MentorProfileResponse getMyMentorProfile(Long userId) {
        MentorProfile profile = mentorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new UserNotFoundException("멘토 프로필을 찾을 수 없습니다"));

        // TODO: admin review flow — Phase 후속
        String rejectedReason = null;
        if (profile.getStatus() == MentorStatus.REJECTED) {
            rejectedReason = historyRepository.findTopByUserIdOrderBySubmittedAtDesc(userId)
                    .map(MentorProfileHistory::getRejectedReason)
                    .orElse(null);
        }
        return MentorProfileResponse.from(profile, rejectedReason);
    }
}
