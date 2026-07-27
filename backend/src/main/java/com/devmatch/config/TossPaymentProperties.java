package com.devmatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 외부 토스 API 실호출 여부를 제어하는 플래그.
 *
 * <p><b>학생 포트폴리오 정책상 실결제·실환불은 금지</b>이며, 두 방향 모두 기본 false 로 차단한다.
 * 키를 잘못 넣어도(예: {@code live_sk_...}) 코드가 한 번 더 막아주는 구조적 안전장치다.
 * 안전의 근거를 "설정"이 아니라 "코드"에 두기 위함이다.
 *
 * <ul>
 *   <li>{@code tossConfirmEnabled} — 결제 승인 API 호출. false 면 외부 호출 없이 내부 상태만 CONFIRMED 로 전이.</li>
 *   <li>{@code tossCancelEnabled} — 결제 취소(환불) API 호출. false 면 외부 호출 없이 내부 상태만 CANCELLED 로 전이.</li>
 * </ul>
 *
 * <p>실호출이 필요할 때만 환경변수로 true 로 올리되, <b>반드시 {@code test_} 접두사 키와 함께</b> 사용한다.
 * 토스는 키 접두사로 샌드박스({@code test_})와 실운영({@code live_})을 완전히 분리한다.
 */
@ConfigurationProperties("app.payment")
public record TossPaymentProperties(
        boolean tossConfirmEnabled,
        boolean tossCancelEnabled
) {
}
