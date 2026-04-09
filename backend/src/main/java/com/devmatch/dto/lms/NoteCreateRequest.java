package com.devmatch.dto.lms;
import com.devmatch.entity.NoteType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor; import lombok.Getter; import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor @AllArgsConstructor
public class NoteCreateRequest {
    @NotNull(message = "매칭 ID는 필수입니다") private Long matchingId;
    @NotNull(message = "노트 유형은 필수입니다") private NoteType type;
    private Long sessionId; private Integer weekNumber;
    @NotBlank(message = "제목은 필수입니다") private String title;
    @NotBlank(message = "내용은 필수입니다") private String content;
    private Integer selfRating;
}
