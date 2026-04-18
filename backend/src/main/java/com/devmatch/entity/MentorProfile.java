package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "mentor_profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MentorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "mentor_profile_courses",
        joinColumns = @JoinColumn(name = "mentor_profile_id"),
        inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    @Builder.Default
    private Set<MentoringCourse> courses = new HashSet<>();

    @Convert(converter = StringListConverter.class)
    @Column(name = "tech_stack", columnDefinition = "TEXT")
    private List<String> techStack;

    @Column(nullable = false)
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

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void updateFromRequest(Set<MentoringCourse> newCourses,
                                  List<String> techStack,
                                  Integer careerYears,
                                  String company, String jobTitle,
                                  String portfolioUrl, String education,
                                  List<String> certifications,
                                  String preferredMenteeLevel,
                                  String bio) {
        this.courses = newCourses;
        this.techStack = techStack;
        this.careerYears = careerYears;
        this.company = company;
        this.jobTitle = jobTitle;
        this.portfolioUrl = portfolioUrl;
        this.education = education;
        this.certifications = certifications;
        this.preferredMenteeLevel = preferredMenteeLevel;
        this.bio = bio;
        this.status = MentorStatus.PENDING;
    }

    public void markApproved() { this.status = MentorStatus.APPROVED; }
    public void markRejected() { this.status = MentorStatus.REJECTED; }
}
