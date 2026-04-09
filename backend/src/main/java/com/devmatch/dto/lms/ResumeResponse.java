package com.devmatch.dto.lms;

import com.devmatch.entity.Resume;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter @AllArgsConstructor @Builder
public class ResumeResponse {
    private Long id;
    private Long menteeId;
    private Long matchingId;
    private Integer version;
    private String fileUrl;
    private String fileName;
    private String mentorFeedback;
    private LocalDateTime feedbackAt;
    private LocalDateTime uploadedAt;

    public static ResumeResponse from(Resume r) {
        return ResumeResponse.builder()
                .id(r.getId()).menteeId(r.getMenteeId()).matchingId(r.getMatchingId())
                .version(r.getVersion()).fileUrl(r.getFileUrl()).fileName(r.getFileName())
                .mentorFeedback(r.getMentorFeedback()).feedbackAt(r.getFeedbackAt())
                .uploadedAt(r.getUploadedAt()).build();
    }
}
