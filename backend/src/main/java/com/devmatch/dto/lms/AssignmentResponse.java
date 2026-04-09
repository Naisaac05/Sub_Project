package com.devmatch.dto.lms;
import com.devmatch.entity.Assignment;
import com.devmatch.entity.AssignmentStatus;
import com.devmatch.entity.AssignmentSubmission;
import com.devmatch.entity.AssignmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter @AllArgsConstructor @Builder
public class AssignmentResponse {
    private Long id; private Long matchingId; private Long mentorId;
    private AssignmentType type; private String title; private String description;
    private LocalDate dueDate; private List<String> referenceUrls;
    private AssignmentStatus status; private SubmissionInfo submission; private LocalDateTime createdAt;

    @Getter @AllArgsConstructor @Builder
    public static class SubmissionInfo {
        private Long id; private String submissionUrl; private String submissionNote;
        private LocalDateTime submittedAt; private String feedbackContent;
        private String grade; private LocalDateTime feedbackAt;
        public static SubmissionInfo from(AssignmentSubmission sub) {
            if (sub == null) return null;
            return SubmissionInfo.builder().id(sub.getId()).submissionUrl(sub.getSubmissionUrl())
                    .submissionNote(sub.getSubmissionNote()).submittedAt(sub.getSubmittedAt())
                    .feedbackContent(sub.getFeedbackContent()).grade(sub.getGrade())
                    .feedbackAt(sub.getFeedbackAt()).build();
        }
    }

    public static AssignmentResponse from(Assignment a) {
        return AssignmentResponse.builder().id(a.getId()).matchingId(a.getMatchingId())
                .mentorId(a.getMentorId()).type(a.getType()).title(a.getTitle())
                .description(a.getDescription()).dueDate(a.getDueDate())
                .referenceUrls(a.getReferenceUrls()).status(a.getStatus())
                .submission(SubmissionInfo.from(a.getSubmission())).createdAt(a.getCreatedAt()).build();
    }
}
