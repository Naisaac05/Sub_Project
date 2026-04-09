package com.devmatch.dto.lms;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

@Getter @NoArgsConstructor @AllArgsConstructor
public class CurriculumCreateRequest {
    @NotNull(message = "매칭 ID는 필수입니다")
    private Long matchingId;
    @NotBlank(message = "커리큘럼 제목은 필수입니다")
    private String title;
    private String description;
    @NotNull(message = "총 주차 수는 필수입니다")
    private Integer totalWeeks;
    @NotNull(message = "시작일은 필수입니다")
    private LocalDate startDate;
    @NotNull(message = "종료일은 필수입니다")
    private LocalDate endDate;
    private String discordUrl;
    private List<WeekRequest> weeks;

    @Getter @NoArgsConstructor @AllArgsConstructor
    public static class WeekRequest {
        private Integer weekNumber;
        private String title;
        private String description;
        private List<String> topics;
        private List<String> resources;
    }
}
