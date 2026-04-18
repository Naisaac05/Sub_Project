package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 신청서 엔티티 — 결제 전에 멘티가 작성하는 기본 정보
 * (현재 실력, 목표 기술 스택, 목표 커리어 등)
 */
@Entity
@Table(name = "applications")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentee_id", nullable = false)
    private User mentee;

    @Column(name = "current_level", nullable = false, length = 50)
    private String currentLevel;

    @Column(name = "target_tech_stack", nullable = false, length = 500)
    private String targetTechStack;

    @Column(name = "career_goal", nullable = false, length = 200)
    private String careerGoal;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "course_type", nullable = false, length = 20)
    private String courseType;

    @Column(name = "desired_months", nullable = false)
    @Builder.Default
    private Integer desiredMonths = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    @Column(nullable = false)
    @Builder.Default
    private Boolean autoMatched = false;

    @Convert(converter = StringListConverter.class)
    @Column(name = "languages", columnDefinition = "TEXT")
    private List<String> languages;

    @Convert(converter = StringListConverter.class)
    @Column(name = "platforms", columnDefinition = "TEXT")
    private List<String> platforms;

    @Column(name = "is_cs_major")
    private Boolean isCsMajor;

    @Convert(converter = StringListConverter.class)
    @Column(name = "learning_paths", columnDefinition = "TEXT")
    private List<String> learningPaths;

    @Column(name = "career_years")
    private String careerYears;

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "project_count")
    private String projectCount;

    @Column(name = "project_description", columnDefinition = "TEXT")
    private String projectDescription;

    @Column(name = "weekday_study_hours")
    private String weekdayStudyHours;

    @Column(name = "weekend_study_hours")
    private String weekendStudyHours;

    @Column(name = "goal")
    private String goal;

    @Column(name = "personality")
    private String personality;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "self_introduction", columnDefinition = "TEXT")
    private String selfIntroduction;

    @Convert(converter = StringListConverter.class)
    @Column(name = "referral_sources", columnDefinition = "TEXT")
    private List<String> referralSources;

    @Column(name = "referral_code")
    private String referralCode;

    @Column(name = "terms_agreed")
    private Boolean termsAgreed;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_mentor_id")
    private User assignedMentor;

    @ElementCollection
    @CollectionTable(name = "application_rejected_mentors", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "mentor_id")
    @Builder.Default
    private Set<Long> rejectedMentors = new HashSet<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void markPaid() {
        this.status = ApplicationStatus.PAYMENT_COMPLETED;
    }

    public void markCompleted() {
        this.status = ApplicationStatus.PAYMENT_COMPLETED;
    }

    public void acceptAutoMatch() {
        this.status = ApplicationStatus.ACCEPTED;
        this.autoMatched = true;
    }

    public void assignMentor(User mentor) {
        this.assignedMentor = mentor;
        this.status = ApplicationStatus.PENDING_MENTOR_APPROVAL;
    }

    public void rejectByCurrentMentor() {
        if (this.assignedMentor != null) {
            this.rejectedMentors.add(this.assignedMentor.getId());
            this.assignedMentor = null;
        }
    }

    public void markMatchingFailed() {
        this.status = ApplicationStatus.MATCHING_FAILED;
        this.assignedMentor = null;
    }
}
