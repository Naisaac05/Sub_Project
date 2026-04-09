package com.devmatch.dto.lms;
import com.devmatch.entity.AssignmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class AssignmentCreateRequest {
    @NotNull(message = "매칭 ID는 필수입니다") private Long matchingId;
    @NotNull(message = "과제 유형은 필수입니다") private AssignmentType type;
    @NotBlank(message = "제목은 필수입니다") private String title;
    private String description;
    private LocalDate dueDate;
    private List<String> referenceUrls;
}
