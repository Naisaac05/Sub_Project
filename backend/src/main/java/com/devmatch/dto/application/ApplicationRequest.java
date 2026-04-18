package com.devmatch.dto.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationRequest {
    private Long menteeId;
    private String currentLevel;
    private String targetTechStack;
    private String careerGoal;
    private String category;
    private String courseType;
    private Integer desiredMonths;
    // New fields
    private List<String> languages;
    private List<String> platforms;
    private Boolean isCsMajor;
    private List<String> learningPaths;
    private String careerYears;
    private String githubUrl;
    private String projectCount;
    private String projectDescription;
    private String weekdayStudyHours;
    private String weekendStudyHours;
    private String goal;
    private String personality;
    private String phone;
    private String selfIntroduction;
    private List<String> referralSources;
    private String referralCode;
    private Boolean termsAgreed;
}
