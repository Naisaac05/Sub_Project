package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "assignment_submissions")
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class AssignmentSubmission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false, unique = true)
    private Assignment assignment;
    @Column(name = "mentee_id", nullable = false)
    private Long menteeId;
    @Column(name = "submission_url", nullable = false, length = 500)
    private String submissionUrl;
    @Column(name = "submission_note", columnDefinition = "TEXT")
    private String submissionNote;
    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;
    @Column(name = "feedback_content", columnDefinition = "TEXT")
    private String feedbackContent;
    @Column(length = 10)
    private String grade;
    @Column(name = "feedback_at")
    private LocalDateTime feedbackAt;

    public void addFeedback(String feedbackContent, String grade) {
        this.feedbackContent = feedbackContent;
        this.grade = grade;
        this.feedbackAt = LocalDateTime.now();
    }
}
