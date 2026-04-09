package com.devmatch.dto.lms;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor; import lombok.Getter; import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class SubmissionRequest {
    @NotBlank(message = "제출 URL은 필수입니다") private String submissionUrl;
    private String submissionNote;
}
