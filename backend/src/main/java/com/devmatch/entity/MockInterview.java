package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mock_interviews")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MockInterview {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "matching_id", nullable = false)
    private Long matchingId;

    @Column(name = "interview_date", nullable = false)
    private LocalDate interviewDate;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "questions_and_answers", columnDefinition = "TEXT")
    private String questionsAndAnswers;

    @Column(name = "mentor_feedback", columnDefinition = "TEXT")
    private String mentorFeedback;

    @Column
    private Integer rating;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
