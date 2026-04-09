package com.devmatch.dto.lms;
import com.devmatch.entity.Curriculum;
import com.devmatch.entity.CurriculumWeek;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter @AllArgsConstructor @Builder
public class CurriculumResponse {
    private Long id;
    private Long matchingId;
    private String title;
    private String description;
    private Integer totalWeeks;
    private LocalDate startDate;
    private LocalDate endDate;
    private String discordUrl;
    private List<WeekResponse> weeks;
    private LocalDateTime createdAt;

    @Getter @AllArgsConstructor @Builder
    public static class WeekResponse {
        private Long id;
        private Integer weekNumber;
        private String title;
        private String description;
        private List<String> topics;
        private List<String> resources;
        private Boolean isCompleted;
        private LocalDateTime completedAt;

        public static WeekResponse from(CurriculumWeek week) {
            return WeekResponse.builder()
                    .id(week.getId()).weekNumber(week.getWeekNumber())
                    .title(week.getTitle()).description(week.getDescription())
                    .topics(week.getTopics()).resources(week.getResources())
                    .isCompleted(week.getIsCompleted()).completedAt(week.getCompletedAt())
                    .build();
        }
    }

    public static CurriculumResponse from(Curriculum curriculum) {
        return CurriculumResponse.builder()
                .id(curriculum.getId()).matchingId(curriculum.getMatchingId())
                .title(curriculum.getTitle()).description(curriculum.getDescription())
                .totalWeeks(curriculum.getTotalWeeks())
                .startDate(curriculum.getStartDate()).endDate(curriculum.getEndDate())
                .discordUrl(curriculum.getDiscordUrl())
                .weeks(curriculum.getWeeks().stream().map(WeekResponse::from).collect(Collectors.toList()))
                .createdAt(curriculum.getCreatedAt())
                .build();
    }
}
