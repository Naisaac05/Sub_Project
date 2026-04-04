package com.devmatch.dto.payment;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreateRequest {

    @NotNull(message = "신청서 ID는 필수입니다")
    private Long applicationId;

    @NotNull(message = "수강 방식은 필수입니다 (IMMEDIATE 또는 EARLY_BIRD)")
    private String courseType;

    @Min(value = 1, message = "수강 개월 수는 1 이상이어야 합니다")
    private Integer monthsBundled = 1;

    // 할부 개월 수 (0=일시불)
    private Integer installmentMonths = 0;
}
