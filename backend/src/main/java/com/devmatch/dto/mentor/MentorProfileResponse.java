package com.devmatch.dto.mentor;

import com.devmatch.dto.course.CourseSummary;
import com.devmatch.entity.MentorProfile;
import com.devmatch.entity.MentorStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class MentorProfileResponse {

    private Long id;
    private Long userId;
    private String name;
    private String email;
    private List<CourseSummary> courses;
    private List<String> techStack;
    private Integer careerYears;
    private String company;
    private String jobTitle;
    private String portfolioUrl;
    private String education;
    private List<String> certifications;
    private String preferredMenteeLevel;
    private String bio;
    private MentorStatus status;
    private String rejectedReason;

    public static MentorProfileResponse from(MentorProfile p, String rejectedReason) {
        return MentorProfileResponse.builder()
                .id(p.getId())
                .userId(p.getUser().getId())
                .name(p.getUser().getName())
                .email(p.getUser().getEmail())
                .courses(p.getCourses().stream().map(CourseSummary::from).toList())
                .techStack(p.getTechStack())
                .careerYears(p.getCareerYears())
                .company(p.getCompany())
                .jobTitle(p.getJobTitle())
                .portfolioUrl(p.getPortfolioUrl())
                .education(p.getEducation())
                .certifications(p.getCertifications())
                .preferredMenteeLevel(p.getPreferredMenteeLevel())
                .bio(p.getBio())
                .status(p.getStatus())
                .rejectedReason(rejectedReason)
                .build();
    }
}
