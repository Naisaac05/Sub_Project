package com.devmatch.dto.matching;

import com.devmatch.entity.MentorProfile;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MentorRecommendResponse {

    private Long mentorId;
    private String name;
    private List<String> specialty;
    private Integer careerYears;
    private String company;
    private String bio;
    private Integer matchScore;

    public static MentorRecommendResponse of(MentorProfile profile, int matchScore) {
        return new MentorRecommendResponse(
                profile.getUser().getId(),
                profile.getUser().getName(),
                profile.getSpecialty(),
                profile.getCareerYears(),
                profile.getCompany(),
                profile.getBio(),
                matchScore
        );
    }
}
