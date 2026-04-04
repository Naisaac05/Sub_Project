package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "matchings")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Matching {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentee_id", nullable = false)
    private User mentee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_result_id")
    private TestResult testResult;

    // 신청서와의 연결
    @Column(name = "application_id")
    private Long applicationId;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MatchingStatus status = MatchingStatus.PENDING;

    @Column(length = 500)
    private String rejectedReason;

    // ===== 무료 체험 관련 필드 =====

    // 무료 체험 만료일 (매칭 생성 시 +7일로 세팅)
    @Column(name = "trial_end_date")
    private LocalDate trialEndDate;

    // 멘토 변경 횟수 (체험 기간 내 무료 변경 추적용)
    @Column(name = "swap_count")
    @Builder.Default
    private Integer swapCount = 0;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void accept() {
        this.status = MatchingStatus.ACCEPTED;
    }

    public void reject(String reason) {
        this.status = MatchingStatus.REJECTED;
        this.rejectedReason = reason;
    }

    /**
     * 무료 체험(TRIAL) 상태로 전환 — 매칭 확정 시 호출
     * 체험 만료일을 현재 날짜로부터 7일 후로 세팅
     */
    public void startTrial() {
        this.status = MatchingStatus.TRIAL;
        this.trialEndDate = LocalDate.now().plusDays(7);
    }

    /**
     * 체험 기간 내 멘토 변경 처리
     * 기존 매칭을 SWAPPED로 전환하고 변경 횟수를 증가시킴
     */
    public void swap() {
        this.status = MatchingStatus.SWAPPED;
        this.swapCount++;
    }

    /**
     * 체험 기간 만료 후 정식 매칭 확정
     */
    public void confirmAfterTrial() {
        this.status = MatchingStatus.ACCEPTED;
    }

    /**
     * 무료 체험 기간인지 확인
     */
    public boolean isInTrialPeriod() {
        return this.status == MatchingStatus.TRIAL
                && this.trialEndDate != null
                && !LocalDate.now().isAfter(this.trialEndDate);
    }
}
