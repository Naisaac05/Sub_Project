package com.devmatch.dto.admin.payment;

import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;

import java.time.LocalDateTime;

public record AdminPaymentListItemResponse(
        Long id,
        String orderId,
        Long userId,
        String userName,
        String userEmail,
        Integer amount,
        PaymentStatus status,
        LocalDateTime createdAt,
        LocalDateTime cancelledAt,
        Long matchingId
) {
    public static AdminPaymentListItemResponse of(Payment p, String userName, String userEmail) {
        return new AdminPaymentListItemResponse(
                p.getId(),
                p.getOrderId(),
                p.getUserId(),
                userName,
                userEmail,
                p.getAmount(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getCancelledAt(),
                p.getMatchingId()
        );
    }
}
