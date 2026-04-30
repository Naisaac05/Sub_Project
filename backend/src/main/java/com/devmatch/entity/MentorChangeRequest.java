package com.devmatch.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "mentor_change_requests", indexes = {
        @Index(name = "idx_mentor_change_status_created", columnList = "status, created_at"),
        @Index(name = "idx_mentor_change_mentee_status", columnList = "mentee_id, status")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MentorChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentee_id", nullable = false)
    private Long menteeId;

    @Column(name = "current_matching_id", nullable = false)
    private Long currentMatchingId;

    @Column(name = "current_mentor_id", nullable = false)
    private Long currentMentorId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MentorChangeRequestStatus status = MentorChangeRequestStatus.PENDING;

    @Column(name = "decided_by_admin_id")
    private Long decidedByAdminId;

    @Column(name = "new_mentor_id")
    private Long newMentorId;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    public void approve(Long adminId, Long newMentorUserId) {
        requirePending();
        this.status = MentorChangeRequestStatus.APPROVED;
        this.decidedByAdminId = adminId;
        this.newMentorId = newMentorUserId;
        this.respondedAt = LocalDateTime.now();
    }

    public void reject(Long adminId, String rejectReason) {
        requirePending();
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("반려 사유는 비어 있을 수 없습니다");
        }
        this.status = MentorChangeRequestStatus.REJECTED;
        this.decidedByAdminId = adminId;
        this.rejectReason = rejectReason;
        this.respondedAt = LocalDateTime.now();
    }

    public void cancel() {
        requirePending();
        this.status = MentorChangeRequestStatus.CANCELLED;
        this.respondedAt = LocalDateTime.now();
    }

    private void requirePending() {
        if (this.status != MentorChangeRequestStatus.PENDING) {
            throw new IllegalStateException(
                    "PENDING 상태에서만 처리할 수 있습니다 (현재: " + this.status + ")");
        }
    }
}
