package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "resumes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Resume {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentee_id", nullable = false)
    private Long menteeId;

    @Column(name = "matching_id", nullable = false)
    private Long matchingId;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_name", nullable = false, length = 200)
    private String fileName;

    @Column(name = "mentor_feedback", columnDefinition = "TEXT")
    private String mentorFeedback;

    @Column(name = "feedback_at")
    private LocalDateTime feedbackAt;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    public void addFeedback(String feedback) {
        this.mentorFeedback = feedback;
        this.feedbackAt = LocalDateTime.now();
    }
}
