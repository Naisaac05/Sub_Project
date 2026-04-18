package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "mentor_profile_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MentorProfileHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Convert(converter = StringListConverter.class)
    @Column(name = "course_keys", columnDefinition = "TEXT", nullable = false)
    private List<String> courseKeys;

    @Convert(converter = StringListConverter.class)
    @Column(name = "tech_stack", columnDefinition = "TEXT")
    private List<String> techStack;

    @Column(name = "career_years", nullable = false)
    private Integer careerYears;

    @Column(length = 100)
    private String company;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    @Column(length = 200)
    private String education;

    @Convert(converter = StringListConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> certifications;

    @Column(name = "preferred_mentee_level", length = 20)
    private String preferredMenteeLevel;

    @Column(length = 1000)
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MentorStatus status = MentorStatus.PENDING;

    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    public void markApproved(Long adminUserId) {
        this.status = MentorStatus.APPROVED;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = adminUserId;
    }

    public void markRejected(Long adminUserId, String reason) {
        this.status = MentorStatus.REJECTED;
        this.rejectedReason = reason;
        this.reviewedAt = LocalDateTime.now();
        this.reviewedBy = adminUserId;
    }
}
