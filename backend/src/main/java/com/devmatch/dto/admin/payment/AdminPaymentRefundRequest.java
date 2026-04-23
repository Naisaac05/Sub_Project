package com.devmatch.dto.admin.payment;

import jakarta.validation.constraints.Size;

public record AdminPaymentRefundRequest(
        @Size(min = 10, max = 500, message = "사유는 10~500자로 입력해주세요")
        String reason
) {}
