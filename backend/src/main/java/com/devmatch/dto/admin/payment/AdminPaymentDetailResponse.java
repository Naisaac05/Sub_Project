package com.devmatch.dto.admin.payment;

import com.devmatch.entity.PaymentStatus;

import java.time.LocalDateTime;

public record AdminPaymentDetailResponse(
        Long id,
        String orderId,
        String paymentKey,
        Integer amount,
        Integer discountApplied,
        Integer installmentMonths,
        String courseType,
        Integer monthsBundled,
        Integer renewalCount,
        PaymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        UserSection user,
        ApplicationSection application,
        MatchingSection matching,  // nullable
        RefundSection refund       // nullable — CANCELLED 일 때 세팅
) {
    public record UserSection(Long id, String name, String email, String role) {}
    public record ApplicationSection(Long id, String category) {}
    public record MatchingSection(Long id, String mentorName, String status) {}
    public record RefundSection(Long processedByAdminId, String processedByAdminName, LocalDateTime cancelledAt, String reason) {}
}
