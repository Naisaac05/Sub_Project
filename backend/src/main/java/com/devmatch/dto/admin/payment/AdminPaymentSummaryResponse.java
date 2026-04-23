package com.devmatch.dto.admin.payment;

public record AdminPaymentSummaryResponse(
        long totalAmount,
        long confirmedCount,
        long refundedAmount,
        double refundRate  // 0.0 ~ 1.0, 소수 3자리 이하 반올림은 FE 표시에서 처리
) {}
