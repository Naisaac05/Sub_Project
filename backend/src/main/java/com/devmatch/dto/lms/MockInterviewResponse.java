package com.devmatch.dto.lms;

import com.devmatch.entity.MockInterview;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @AllArgsConstructor @Builder
public class MockInterviewResponse {
    private Long id;
    private Long matchingId;
    private LocalDate interviewDate;
    private String topic;
    private String questionsAndAnswers;
    private String mentorFeedback;
    private Integer rating;
    private LocalDateTime createdAt;

    public static MockInterviewResponse from(MockInterview mi) {
        return MockInterviewResponse.builder()
                .id(mi.getId()).matchingId(mi.getMatchingId())
                .interviewDate(mi.getInterviewDate()).topic(mi.getTopic())
                .questionsAndAnswers(mi.getQuestionsAndAnswers())
                .mentorFeedback(mi.getMentorFeedback())
                .rating(mi.getRating()).createdAt(mi.getCreatedAt()).build();
    }
}
