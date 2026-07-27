package com.devmatch.dto.payment;

import com.devmatch.entity.EnrollmentPlan;
import com.devmatch.service.PaymentService.PricingResult;

/**
 * 결제 플랜 카드에 그대로 표시되는 가격 내역.
 *
 * <p>{@code rawTotal} 은 할인 전 정가(취소선 표시용), {@code finalAmount} 는 실제 청구 금액이다.
 * 프론트엔드는 이 값을 계산 없이 그대로 렌더링해야 화면 표시가와 청구액이 갈라지지 않는다.
 */
public record PlanPricingResponse(
        EnrollmentPlan plan,
        int unitPrice,
        int monthsBundled,
        int renewalCount,
        int rawTotal,
        int bundleDiscount,
        int planDiscount,
        int discountAmount,
        int finalAmount
) {
    public static PlanPricingResponse from(PricingResult result) {
        return new PlanPricingResponse(
                result.plan(),
                result.unitPrice(),
                result.monthsBundled(),
                result.renewalCount(),
                result.rawTotal(),
                result.bundleDiscount(),
                result.planDiscount(),
                result.discountAmount(),
                result.finalAmount()
        );
    }
}
