package com.devmatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 강제 환불 시 외부 토스 API 호출 여부를 제어하는 플래그.
 *
 * <ul>
 *   <li>prod: true — 실제 토스 결제 취소 API 를 호출</li>
 *   <li>dev/local: false — 외부 호출 없이 내부 상태만 CANCELLED 로 전이</li>
 * </ul>
 */
@ConfigurationProperties("app.payment")
public record TossCancelProperties(boolean tossCancelEnabled) {
}
