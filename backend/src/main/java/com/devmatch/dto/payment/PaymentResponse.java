package com.devmatch.dto.payment;

import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private Long userId;
    private Long matchingId;
    private String orderId;
    private String paymentKey;
    private Integer amount;
    private PaymentStatus status;
    private String cancelReason;
    private LocalDateTime createdAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .userId(payment.getUserId())
                .matchingId(payment.getMatchingId())
                .orderId(payment.getOrderId())
                .paymentKey(payment.getPaymentKey())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .cancelReason(payment.getCancelReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
