package com.devmatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 토스페이먼츠 API 연동 서비스 (Stub 구현)
 * TODO: 실제 토스페이먼츠 API 연동 시 RestTemplate/WebClient로 교체
 */
@Service
@Slf4j
public class TossPaymentService {

    @Value("${toss.payments.secret-key}")
    private String secretKey;

    /**
     * 토스페이먼츠 결제 승인 API 호출
     * POST https://api.tosspayments.com/v1/payments/confirm
     *
     * TODO: 실제 구현 예시
     * HttpHeaders headers = new HttpHeaders();
     * headers.setBasicAuth(secretKey, "");
     * headers.setContentType(MediaType.APPLICATION_JSON);
     *
     * Map<String, Object> body = Map.of(
     *     "paymentKey", paymentKey,
     *     "orderId", orderId,
     *     "amount", amount
     * );
     *
     * restTemplate.postForEntity(
     *     "https://api.tosspayments.com/v1/payments/confirm",
     *     new HttpEntity<>(body, headers),
     *     Map.class
     * );
     */
    public boolean confirmPayment(String paymentKey, String orderId, Integer amount) {
        log.warn("[STUB] 토스페이먼츠 결제 승인 호출 - paymentKey: {}, orderId: {}, amount: {}",
                paymentKey, orderId, amount);
        // Stub: 항상 성공 반환
        return true;
    }

    /**
     * 토스페이먼츠 결제 취소 API 호출
     * POST https://api.tosspayments.com/v1/payments/{paymentKey}/cancel
     *
     * TODO: 실제 구현 예시
     * HttpHeaders headers = new HttpHeaders();
     * headers.setBasicAuth(secretKey, "");
     * headers.setContentType(MediaType.APPLICATION_JSON);
     *
     * Map<String, Object> body = Map.of("cancelReason", cancelReason);
     *
     * restTemplate.postForEntity(
     *     "https://api.tosspayments.com/v1/payments/" + paymentKey + "/cancel",
     *     new HttpEntity<>(body, headers),
     *     Map.class
     * );
     */
    public boolean cancelPayment(String paymentKey, String cancelReason) {
        log.warn("[STUB] 토스페이먼츠 결제 취소 호출 - paymentKey: {}, reason: {}",
                paymentKey, cancelReason);
        // Stub: 항상 성공 반환
        return true;
    }
}
