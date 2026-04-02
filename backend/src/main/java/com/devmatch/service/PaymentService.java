package com.devmatch.service;

import com.devmatch.dto.payment.PaymentCancelRequest;
import com.devmatch.dto.payment.PaymentConfirmRequest;
import com.devmatch.dto.payment.PaymentCreateRequest;
import com.devmatch.dto.payment.PaymentResponse;
import com.devmatch.entity.Matching;
import com.devmatch.entity.MatchingStatus;
import com.devmatch.entity.Payment;
import com.devmatch.entity.User;
import com.devmatch.exception.*;
import com.devmatch.repository.MatchingRepository;
import com.devmatch.repository.PaymentRepository;
import com.devmatch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final MatchingRepository matchingRepository;
    private final UserRepository userRepository;
    private final TossPaymentService tossPaymentService;

    @Transactional
    public PaymentResponse createPayment(Long userId, PaymentCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다"));

        Matching matching = matchingRepository.findById(request.getMatchingId())
                .orElseThrow(() -> new MatchingNotFoundException("매칭을 찾을 수 없습니다"));

        // 매칭 ACCEPTED 상태 확인
        if (matching.getStatus() != MatchingStatus.ACCEPTED) {
            throw new PaymentFailedException("수락된 매칭에 대해서만 결제할 수 있습니다");
        }

        // 매칭 당사자(멘티) 확인
        if (!matching.getMentee().getId().equals(userId)) {
            throw new PaymentFailedException("본인의 매칭에 대해서만 결제할 수 있습니다");
        }

        // 중복 결제 확인
        if (paymentRepository.existsByMatchingId(matching.getId())) {
            throw new DuplicatePaymentException("이미 해당 매칭에 대한 결제가 존재합니다");
        }

        String orderId = "DEVMATCH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .user(user)
                .matching(matching)
                .orderId(orderId)
                .amount(request.getAmount())
                .build();

        payment = paymentRepository.save(payment);
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse confirmPayment(Long userId, PaymentConfirmRequest request) {
        Payment payment = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다"));

        // 결제 소유자 확인
        if (!payment.getUser().getId().equals(userId)) {
            throw new PaymentFailedException("본인의 결제만 승인할 수 있습니다");
        }

        // 금액 검증
        if (!payment.getAmount().equals(request.getAmount())) {
            payment.fail();
            throw new PaymentFailedException("결제 금액이 일치하지 않습니다");
        }

        // 토스페이먼츠 결제 승인 요청
        boolean success = tossPaymentService.confirmPayment(
                request.getPaymentKey(), request.getOrderId(), request.getAmount());

        if (!success) {
            payment.fail();
            throw new PaymentFailedException("토스페이먼츠 결제 승인에 실패했습니다");
        }

        payment.confirm(request.getPaymentKey());
        return PaymentResponse.from(payment);
    }

    @Transactional
    public PaymentResponse cancelPayment(Long userId, Long paymentId, PaymentCancelRequest request) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다"));

        // 결제 소유자 확인
        if (!payment.getUser().getId().equals(userId)) {
            throw new PaymentFailedException("본인의 결제만 취소할 수 있습니다");
        }

        // 토스페이먼츠 결제 취소 요청
        if (payment.getPaymentKey() != null) {
            boolean success = tossPaymentService.cancelPayment(
                    payment.getPaymentKey(), request.getCancelReason());
            if (!success) {
                throw new PaymentFailedException("토스페이먼츠 결제 취소에 실패했습니다");
            }
        }

        payment.cancel(request.getCancelReason());
        return PaymentResponse.from(payment);
    }

    public List<PaymentResponse> getMyPayments(Long userId) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(PaymentResponse::from)
                .collect(Collectors.toList());
    }

    public PaymentResponse getPayment(Long userId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException("결제 정보를 찾을 수 없습니다"));

        if (!payment.getUser().getId().equals(userId)) {
            throw new PaymentFailedException("본인의 결제만 조회할 수 있습니다");
        }

        return PaymentResponse.from(payment);
    }
}
