package com.devmatch.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfirmRequest {

    @NotBlank(message = "paymentKey는 필수입니다")
    private String paymentKey;

    @NotBlank(message = "orderId는 필수입니다")
    private String orderId;

    @NotNull(message = "결제 금액은 필수입니다")
    private Integer amount;
}
