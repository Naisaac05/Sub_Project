package com.devmatch.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class TossPaymentConfig {

    @Value("${toss.payments.secret-key:test_sk_placeholder}")
    private String secretKey;

    @Value("${toss.payments.base-url:https://api.tosspayments.com/v1}")
    private String baseUrl;

    @Bean
    public RestTemplate tossRestTemplate() {
        return new RestTemplate();
    }

    /** 토스 멱등키 최대 길이 (토스페이먼츠 규격). */
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 300;

    /**
     * 토스페이먼츠 API 인증 헤더를 생성합니다.
     * Basic Auth 방식: Base64(secretKey + ":")
     */
    public HttpHeaders createTossHeaders() {
        HttpHeaders headers = new HttpHeaders();
        String encodedKey = Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + encodedKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    /**
     * 인증 헤더에 {@code Idempotency-Key} 를 더해 생성합니다.
     *
     * <p>같은 결제 시도는 <b>항상 같은 키</b>, 다른 결제는 <b>반드시 다른 키</b>여야 한다.
     * 매번 새 UUID 를 만들면 토스 입장에서 전부 새 요청이 되어 멱등성이 무력화된다.
     *
     * <p>이 헤더는 우리 내부 방어(분산 락·상태 체크·DB 유니크 제약)가 <b>구조적으로 닫을 수 없는</b>
     * 창을 닫는다: 토스 호출이 성공한 직후 DB 저장 전에 프로세스가 죽으면, 우리 DB 에는 흔적이
     * 없어 재시도가 결제를 다시 시도하게 된다. 이때 같은 멱등키를 받은 토스가 중복 집행을 막아준다.
     */
    public HttpHeaders createTossHeaders(String idempotencyKey) {
        HttpHeaders headers = createTossHeaders();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH
                    ? idempotencyKey.substring(0, MAX_IDEMPOTENCY_KEY_LENGTH)
                    : idempotencyKey;
            headers.set("Idempotency-Key", key);
        }
        return headers;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getSecretKey() {
        return secretKey;
    }
}
