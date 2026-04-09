package com.devmatch.dto.lms;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor; import lombok.Getter; import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class FeedbackRequest {
    @NotBlank(message = "피드백 내용은 필수입니다") private String feedbackContent;
    private String grade;
}
