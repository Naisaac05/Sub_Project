package com.devmatch.service;

import com.devmatch.dto.payment.PaymentCancelRequest;
import com.devmatch.dto.payment.PaymentConfirmRequest;
import com.devmatch.dto.payment.PaymentCreateRequest;
import com.devmatch.dto.payment.PaymentResponse;
import com.devmatch.entity.Application;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.DuplicatePaymentException;
import com.devmatch.exception.PaymentFailedException;
import com.devmatch.exception.PaymentNotFoundException;
import com.devmatch.repository.ApplicationRepository;
import com.devmatch.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final ApplicationRepository applicationRepository;
    private final TossPaymentService tossPaymentService;

    // ===== 가격 정책 상수 =====
    private static final int BASE_PRICE = 990_000;           // 기본 1개월 가격: 99만원
    private static final int FIRST_RENEWAL_PRICE = 990_000;  // 1회 연장: 99만원
    private static final int SECOND_RENEWAL_PRICE = 890_000; // 2회 연장: 89만원
    private static final int MAX_RENEWAL_PRICE = 790_000;    // 3회+ 연장: 79만원

    // 묶음 할인율 (3개월 이상)
    private static final double BUNDLE_3_DISCOUNT = 0.05;   // 3개월: 5%
    private static final double BUNDLE_4_DISCOUNT = 0.10;   // 4개월: 10%
    private static final double BUNDLE_5_DISCOUNT = 0.15;   // 5개월: 15%
    private static final double BUNDLE_6_PLUS_DISCOUNT = 0.20; // 6개월+: 20%

    /**
     * 결제 가격 미리보기 (결제 생성 전 가격 확인용)
     * 프론트엔드에서 개월 수 슬라이더 조절 시 호출하여 실시간 가격을 보여줍니다.
     */
    public PricingResult calculatePricing(Long userId, int monthsBundled) {
        long confirmedCount = paymentRepository.countByUserIdAndStatus(userId, PaymentStatus.CONFIRMED);
        int renewalCount = (int) confirmedCount;

        int unitPrice = getUnitPrice(renewalCount);
        int rawTotal = unitPrice * monthsBundled;
        double discountRate = getBundleDiscountRate(monthsBundled);
        int discountAmount = (int) Math.round(rawTotal * discountRate);
        int finalAmount = rawTotal - discountAmount;

        return new PricingResult(unitPrice, monthsBundled, renewalCount, discountRate, discountAmount, finalAmount);
    }

    /**
     * 결제 생성 (PENDING 상태)
     * 프로세스: 신청서 작성 → 결제 생성 → 토스 SDK 호출 → 결제 승인
     */
    @Transactional
    public PaymentResponse createPayment(Long userId, PaymentCreateRequest request) {
        // 중복 결제 확인
        if (paymentRepository.existsByApplicationId(request.getApplicationId())) {
            throw new DuplicatePaymentException("이미 해당 신청서에 대한 결제가 존재합니다");
        }

        // 신청서 존재 확인
        Application application = applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new PaymentFailedException("신청서를 찾을 수 없습니다: " + request.getApplicationId()));

        // 연장 회차 자동 계산
        long confirmedCount = paymentRepository.countByUserIdAndStatus(userId, PaymentStatus.CONFIRMED);
        int renewalCount = (int) confirmedCount;

        // 동적 금액 계산
        int months = request.getMonthsBundled() != null ? request.getMonthsBundled() : 1;
        int unitPrice = getUnitPrice(renewalCount);
        int rawTotal = unitPrice * months;
        double discountRate = getBundleDiscountRate(months);
        int discountAmount = (int) Math.round(rawTotal * discountRate);
        int finalAmount = rawTotal - discountAmount;

        // orderId 자동 생성
        String orderId = "DEVMATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .userId(userId)
                .applicationId(request.getApplicationId())
                .orderId(orderId)
                .amount(finalAmount)
                .courseType(request.getCourseType())
                .monthsBundled(months)
                .renewalCount(renewalCount)
                .discountApplied(discountAmount)
                .installmentMonths(request.getInstallmentMonths() != null ? request.getInstallmentMonths() : 0)
                .build();

        Payment saved = paymentRepository.save(payment);

        // 신청서 상태 업데이트
        application.markPaid();

        log.info("[Payment] 결제 생성 — orderId: {}, amount: {} (할인: {}원, 연장 {}회차, {}개월)",
                orderId, finalAmount, discountAmount, renewalCount, months);
        return PaymentResponse.from(saved);
    }

    /**
     * 결제 승인 (토스페이먼츠 API 호출)
     */
    @Transactional
    public PaymentResponse confirmPayment(Long userId, PaymentConfirmRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + request.getOrderId()));

        if (!payment.getUserId().equals(userId)) {
            throw new PaymentFailedException("본인의 결제만 승인할 수 있습니다");
        }

        if (!payment.getAmount().equals(request.getAmount())) {
            payment.fail();
            throw new PaymentFailedException("결제 금액이 일치하지 않습니다. 요청: "
                    + request.getAmount() + ", 실제: " + payment.getAmount());
        }

        boolean confirmed = tossPaymentService.confirmPayment(
                request.getPaymentKey(),
                request.getOrderId(),
                request.getAmount()
        );

        if (confirmed) {
            payment.confirm(request.getPaymentKey());
            log.info("[Payment] 결제 승인 완료 — orderId: {}, paymentKey: {}",
                    request.getOrderId(), request.getPaymentKey());
        } else {
            payment.fail();
            throw new PaymentFailedException("토스페이먼츠 결제 승인에 실패했습니다");
        }

        return PaymentResponse.from(payment);
    }

    /**
     * 결제 취소 (토스페이먼츠 API 호출)
     */
    @Transactional
    public PaymentResponse cancelPayment(Long userId, Long paymentId, PaymentCancelRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + paymentId));

        if (!payment.getUserId().equals(userId)) {
            throw new PaymentFailedException("본인의 결제만 취소할 수 있습니다");
        }

        if (payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw new PaymentFailedException("승인된 결제만 취소할 수 있습니다. 현재 상태: " + payment.getStatus());
        }

        String cancelReason = request.getCancelReason() != null
                ? request.getCancelReason()
                : "사용자 요청에 의한 취소";

        boolean cancelled = tossPaymentService.cancelPayment(payment.getPaymentKey(), cancelReason);

        if (cancelled) {
            payment.cancel(cancelReason);
            log.info("[Payment] 결제 취소 완료 — paymentId: {}, reason: {}", paymentId, cancelReason);
        } else {
            throw new PaymentFailedException("토스페이먼츠 결제 취소에 실패했습니다");
        }

        return PaymentResponse.from(payment);
    }

    /**
     * 내 결제 목록 조회
     */
    public List<PaymentResponse> getMyPayments(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PaymentResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 결제 상세 조회
     */
    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + paymentId));

        if (!payment.getUserId().equals(userId)) {
            throw new PaymentFailedException("본인의 결제 정보만 조회할 수 있습니다");
        }

        return PaymentResponse.from(payment);
    }

    // ===== 가격 정책 엔진 (내부 로직) =====

    /**
     * 연장 회차에 따른 월 단가 산정
     * 0회(최초): 99만원 → 1회 연장: 99만원 → 2회 연장: 89만원 → 3회+: 79만원
     */
    private int getUnitPrice(int renewalCount) {
        if (renewalCount == 0) return BASE_PRICE;
        if (renewalCount == 1) return FIRST_RENEWAL_PRICE;
        if (renewalCount == 2) return SECOND_RENEWAL_PRICE;
        return MAX_RENEWAL_PRICE;
    }

    /**
     * 묶음 결제 할인율
     * 1~2개월: 0% → 3개월: 5% → 4개월: 10% → 5개월: 15% → 6개월+: 20%
     */
    private double getBundleDiscountRate(int months) {
        if (months < 3) return 0.0;
        if (months == 3) return BUNDLE_3_DISCOUNT;
        if (months == 4) return BUNDLE_4_DISCOUNT;
        if (months == 5) return BUNDLE_5_DISCOUNT;
        return BUNDLE_6_PLUS_DISCOUNT;
    }

    /**
     * 가격 계산 결과 DTO (내부용)
     */
    public record PricingResult(
            int unitPrice,
            int monthsBundled,
            int renewalCount,
            double discountRate,
            int discountAmount,
            int finalAmount
    ) {}
}
