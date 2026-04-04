package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
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

    // 현재 실력 수준 (예: "BEGINNER", "INTERMEDIATE", "ADVANCED")
    @Column(name = "current_level", nullable = false, length = 50)
    private String currentLevel;

    // 목표 기술 스택 (예: "Java, Spring Boot, JPA")
    @Column(name = "target_tech_stack", nullable = false, length = 500)
    private String targetTechStack;

    // 목표 커리어 (예: "백엔드 개발자", "풀스택 개발자")
    @Column(name = "career_goal", nullable = false, length = 200)
    private String careerGoal;

    // 희망 코스 카테고리 (예: "Java Backend", "Frontend React")
    @Column(nullable = false, length = 50)
    private String category;

    // 수강 방식: "IMMEDIATE" (즉시 시작) or "EARLY_BIRD" (얼리버드)
    @Column(name = "course_type", nullable = false, length = 20)
    private String courseType;

    // 희망 수강 개월 수
    @Column(name = "desired_months", nullable = false)
    @Builder.Default
    private Integer desiredMonths = 1;

    // 신청서 상태: DRAFT, SUBMITTED, PAID, COMPLETED
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "SUBMITTED";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void markPaid() {
        this.status = "PAID";
    }

    public void markCompleted() {
        this.status = "COMPLETED";
    }
}
