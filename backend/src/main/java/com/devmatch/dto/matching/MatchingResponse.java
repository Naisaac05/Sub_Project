package com.devmatch.dto.matching;

import com.devmatch.entity.Matching;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class MatchingResponse {

    private Long id;
    private Long menteeId;
    private String menteeName;
    private Long mentorId;
    private String mentorName;
    private String category;
    private String message;
    private String status;
    private String rejectedReason;
    private Integer testScore;
    private LocalDateTime createdAt;

    public static MatchingResponse from(Matching matching) {
        Integer testScore = null;
        if (matching.getTestResult() != null) {
            testScore = matching.getTestResult().getTotalScore();
        }

        return new MatchingResponse(
                matching.getId(),
                matching.getMentee().getId(),
                matching.getMentee().getName(),
                matching.getMentor().getId(),
                matching.getMentor().getName(),
                matching.getCategory(),
                matching.getMessage(),
                matching.getStatus().name(),
                matching.getRejectedReason(),
                testScore,
                matching.getCreatedAt()
        );
    }
}
