package com.devmatch.dto.lms;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor; import lombok.Getter; import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class NoteCommentRequest {
    @NotBlank(message = "코멘트 내용은 필수입니다") private String content;
}
