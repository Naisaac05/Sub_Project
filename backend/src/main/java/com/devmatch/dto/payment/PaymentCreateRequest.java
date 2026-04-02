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

    @NotNull(message = "매칭 ID는 필수입니다")
    private Long matchingId;

    @NotNull(message = "결제 금액은 필수입니다")
    @Min(value = 1000, message = "최소 결제 금액은 1,000원입니다")
    private Integer amount;
}
