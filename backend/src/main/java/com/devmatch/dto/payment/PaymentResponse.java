package com.devmatch.dto.payment;

import com.devmatch.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private String orderId;
    private String paymentKey;
    private Integer amount;
    private String status;
    private Long matchingId;
    private String cancelReason;
    private LocalDateTime createdAt;

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getPaymentKey(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getMatching().getId(),
                payment.getCancelReason(),
                payment.getCreatedAt()
        );
    }
}
