package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 성향 조사 엔티티 — 결제 완료 후 멘티가 작성하는 상세 설문
 * (희망 피드백 스타일, 진행 방식, 학습 수준, 목표 커리어 등)
 */
@Entity
@Table(name = "survey_responses")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SurveyResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentee_id", nullable = false)
    private User mentee;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // 희망 기술 스택 (예: "Java, Spring Boot, JPA, MySQL")
    @Column(name = "tech_stack", length = 500)
    private String techStack;

    // 현재 학습 수준 (예: "BEGINNER", "INTERMEDIATE", "ADVANCED")
    @Column(name = "current_level", length = 50)
    private String currentLevel;

    // 학습 스타일 (예: "실습 위주", "이론 + 실습 병행", "프로젝트 기반")
    @Column(name = "learning_style", length = 500)
    private String learningStyle;

    // 희망 피드백 스타일 (예: "꼼꼼한 코드리뷰", "방향성 제시", "자율적 진행")
    @Column(name = "feedback_preference", length = 500)
    private String feedbackPreference;

    // 목표 커리어 (예: "네카라쿠배 백엔드 개발자", "스타트업 풀스택")
    @Column(name = "career_goal", length = 500)
    private String careerGoal;

    // 희망 멘토링 진행 방식 (예: "주 1회 화상 미팅", "주 2회 코드리뷰")
    @Column(name = "mentoring_method", length = 500)
    private String mentoringMethod;

    // 희망 스케줄 — 가능한 요일과 시간대 (JSON 형태)
    // 예: "MON:19:00-21:00,WED:20:00-22:00,SAT:10:00-12:00"
    @Column(name = "preferred_schedule", length = 500)
    private String preferredSchedule;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
