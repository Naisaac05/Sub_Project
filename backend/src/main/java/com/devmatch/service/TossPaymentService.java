package com.devmatch.service;

import com.devmatch.config.TossPaymentConfig;
import com.devmatch.exception.PaymentFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 토스페이먼츠 REST API를 호출하여 결제를 승인/취소합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TossPaymentService {

    private final RestTemplate tossRestTemplate;
    private final TossPaymentConfig tossConfig;

    /**
     * 결제 승인 API 호출
     * POST https://api.tosspayments.com/v1/payments/confirm
     *
     * @return true if successful
     * @throws PaymentFailedException if API call fails
     */
    public boolean confirmPayment(String paymentKey, String orderId, Integer amount) {
        String url = tossConfig.getBaseUrl() + "/payments/confirm";

        Map<String, Object> body = Map.of(
                "paymentKey", paymentKey,
                "orderId", orderId,
                "amount", amount
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, tossConfig.createTossHeaders());

            ResponseEntity<String> response = tossRestTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[TossPayment] 결제 승인 성공 — paymentKey: {}, orderId: {}, amount: {}",
                        paymentKey, orderId, amount);
                return true;
            }

            log.error("[TossPayment] 결제 승인 실패 — status: {}, body: {}",
                    response.getStatusCode(), response.getBody());
            throw new PaymentFailedException("결제 승인에 실패했습니다: " + response.getBody());

        } catch (HttpClientErrorException e) {
            log.error("[TossPayment] 결제 승인 API 오류 — status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentFailedException("결제 승인에 실패했습니다: " + e.getResponseBodyAsString());
        } catch (PaymentFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TossPayment] 결제 승인 중 예외 발생: {}", e.getMessage());
            throw new PaymentFailedException("결제 승인 중 오류가 발생했습니다");
        }
    }

    /**
     * 결제 취소 API 호출
     * POST https://api.tosspayments.com/v1/payments/{paymentKey}/cancel
     *
     * @return true if successful
     * @throws PaymentFailedException if API call fails
     */
    public boolean cancelPayment(String paymentKey, String cancelReason) {
        String url = tossConfig.getBaseUrl() + "/payments/" + paymentKey + "/cancel";

        Map<String, String> body = Map.of(
                "cancelReason", cancelReason != null ? cancelReason : "사용자 요청에 의한 취소"
        );

        try {
            HttpEntity<Map<String, String>> request = new HttpEntity<>(body, tossConfig.createTossHeaders());

            ResponseEntity<String> response = tossRestTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[TossPayment] 결제 취소 성공 — paymentKey: {}, reason: {}",
                        paymentKey, cancelReason);
                return true;
            }

            log.error("[TossPayment] 결제 취소 실패 — status: {}", response.getStatusCode());
            throw new PaymentFailedException("결제 취소에 실패했습니다");

        } catch (HttpClientErrorException e) {
            log.error("[TossPayment] 결제 취소 API 오류 — status: {}, body: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentFailedException("결제 취소에 실패했습니다: " + e.getResponseBodyAsString());
        } catch (PaymentFailedException e) {
            throw e;
        } catch (Exception e) {
            log.error("[TossPayment] 결제 취소 중 예외 발생: {}", e.getMessage());
            throw new PaymentFailedException("결제 취소 중 오류가 발생했습니다");
        }
    }
}
