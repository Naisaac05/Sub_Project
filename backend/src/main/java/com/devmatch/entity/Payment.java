package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor @Builder
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 신청서 연결 (결제는 신청서 기반으로 생성됨)
    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    // 매칭 연결 (결제 후 추천→선택 완료 시 세팅됨, 처음에는 null)
    @Column(name = "matching_id", unique = true)
    private Long matchingId;

    @Column(name = "order_id", nullable = false, unique = true, length = 100)
    private String orderId;

    @Column(name = "payment_key", unique = true, length = 200)
    private String paymentKey;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    // 수강 방식: "IMMEDIATE" (즉시 시작) or "EARLY_BIRD" (얼리버드)
    @Column(name = "course_type", length = 20)
    private String courseType;

    // 묶음 결제 개월 수
    @Column(name = "months_bundled")
    @Builder.Default
    private Integer monthsBundled = 1;

    // 연장 회차 (0=최초 결제, 1=1회 연장, 2=2회 연장...)
    @Column(name = "renewal_count")
    @Builder.Default
    private Integer renewalCount = 0;

    // 적용된 할인 금액 (원)
    @Column(name = "discount_applied")
    @Builder.Default
    private Integer discountApplied = 0;

    // 할부 개월 수
    @Column(name = "installment_months")
    @Builder.Default
    private Integer installmentMonths = 0;

    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // — 변경 메서드 —

    public void confirm(String paymentKey) {
        this.status = PaymentStatus.CONFIRMED;
        this.paymentKey = paymentKey;
    }

    public void cancel(String cancelReason) {
        this.status = PaymentStatus.CANCELLED;
        this.cancelReason = cancelReason;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }

    public void linkMatching(Long matchingId) {
        this.matchingId = matchingId;
    }
}
