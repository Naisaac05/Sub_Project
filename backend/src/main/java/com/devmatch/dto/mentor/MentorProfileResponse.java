package com.devmatch.dto.mentor;

import com.devmatch.entity.MentorProfile;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class MentorProfileResponse {

    private Long id;
    private Long userId;
    private String name;
    private String email;
    private List<String> specialty;
    private Integer careerYears;
    private String company;
    private String bio;
    private String status;

    public static MentorProfileResponse from(MentorProfile profile) {
        return new MentorProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getUser().getName(),
                profile.getUser().getEmail(),
                profile.getSpecialty(),
                profile.getCareerYears(),
                profile.getCompany(),
                profile.getBio(),
                profile.getStatus().name()
        );
    }
}
