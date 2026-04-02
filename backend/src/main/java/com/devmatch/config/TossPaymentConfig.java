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

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getSecretKey() {
        return secretKey;
    }
}
