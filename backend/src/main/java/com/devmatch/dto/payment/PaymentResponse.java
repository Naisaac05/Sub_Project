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
    private Long applicationId;
    private Long matchingId;
    private String orderId;
    private String paymentKey;
    private Integer amount;
    private PaymentStatus status;
    private String courseType;
    private Integer monthsBundled;
    private Integer renewalCount;
    private Integer discountApplied;
    private Integer installmentMonths;
    private String cancelReason;
    private LocalDateTime createdAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .userId(payment.getUserId())
                .applicationId(payment.getApplicationId())
                .matchingId(payment.getMatchingId())
                .orderId(payment.getOrderId())
                .paymentKey(payment.getPaymentKey())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .courseType(payment.getCourseType())
                .monthsBundled(payment.getMonthsBundled())
                .renewalCount(payment.getRenewalCount())
                .discountApplied(payment.getDiscountApplied())
                .installmentMonths(payment.getInstallmentMonths())
                .cancelReason(payment.getCancelReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
