package com.devmatch.service;

import com.devmatch.dto.payment.PaymentCancelRequest;
import com.devmatch.dto.payment.PaymentConfirmRequest;
import com.devmatch.dto.payment.PaymentCreateRequest;
import com.devmatch.dto.payment.PaymentResponse;
import com.devmatch.entity.Payment;
import com.devmatch.entity.PaymentStatus;
import com.devmatch.exception.DuplicatePaymentException;
import com.devmatch.exception.PaymentFailedException;
import com.devmatch.exception.PaymentNotFoundException;
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
    private final TossPaymentService tossPaymentService;

    /**
     * 결제 생성 (PENDING 상태)
     * orderId를 자동 생성하고, 프론트엔드에서 토스 SDK 호출에 사용합니다.
     */
    @Transactional
    public PaymentResponse createPayment(Long userId, PaymentCreateRequest request) {
        // 중복 결제 확인
        if (paymentRepository.existsByMatchingId(request.getMatchingId())) {
            throw new DuplicatePaymentException("이미 해당 매칭에 대한 결제가 존재합니다");
        }

        // orderId 자동 생성
        String orderId = "DEVMATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .userId(userId)
                .matchingId(request.getMatchingId())
                .orderId(orderId)
                .amount(request.getAmount())
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("[Payment] 결제 생성 — orderId: {}, amount: {}", orderId, request.getAmount());
        return PaymentResponse.from(saved);
    }

    /**
     * 결제 승인 (토스페이먼츠 API 호출)
     * 프론트엔드에서 토스 SDK 결제 완료 후 호출됩니다.
     */
    @Transactional
    public PaymentResponse confirmPayment(Long userId, PaymentConfirmRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다: " + request.getOrderId()));

        // 결제 소유자 확인
        if (!payment.getUserId().equals(userId)) {
            throw new PaymentFailedException("본인의 결제만 승인할 수 있습니다");
        }

        // 금액 일치 검증 (보안)
        if (!payment.getAmount().equals(request.getAmount())) {
            payment.fail();
            throw new PaymentFailedException("결제 금액이 일치하지 않습니다. 요청: "
                    + request.getAmount() + ", 실제: " + payment.getAmount());
        }

        // 토스 결제 승인 API 호출
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

        // 결제 소유자 확인
        if (!payment.getUserId().equals(userId)) {
            throw new PaymentFailedException("본인의 결제만 취소할 수 있습니다");
        }

        // CONFIRMED 상태만 취소 가능
        if (payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw new PaymentFailedException("승인된 결제만 취소할 수 있습니다. 현재 상태: " + payment.getStatus());
        }

        // 토스 결제 취소 API 호출
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
}
