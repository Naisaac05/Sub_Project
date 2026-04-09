package com.devmatch.dto.lms;

import com.devmatch.entity.Matching;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
@Builder
public class EnrollmentResponse {

    private Long matchingId;
    private String menteeName;
    private String mentorName;
    private String category;
    private String status;
    private LocalDate startDate;
    private LocalDate trialEndDate;

    public static EnrollmentResponse from(Matching matching) {
        return EnrollmentResponse.builder()
                .matchingId(matching.getId())
                .menteeName(matching.getMentee().getName())
                .mentorName(matching.getMentor().getName())
                .category(matching.getCategory())
                .status(matching.getStatus().name())
                .startDate(matching.getCreatedAt().toLocalDate())
                .trialEndDate(matching.getTrialEndDate())
                .build();
    }
}
