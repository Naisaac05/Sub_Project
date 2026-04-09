package com.devmatch.dto.lms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Getter @NoArgsConstructor @AllArgsConstructor
public class MockInterviewCreateRequest {
    @NotNull(message = "매칭 ID는 필수입니다")
    private Long matchingId;
    @NotNull(message = "면접 날짜는 필수입니다")
    private LocalDate interviewDate;
    @NotBlank(message = "주제는 필수입니다")
    private String topic;
    private String questionsAndAnswers;
    private String mentorFeedback;
    private Integer rating;
}
