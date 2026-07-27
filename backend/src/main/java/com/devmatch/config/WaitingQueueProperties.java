package com.devmatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 결제 대기열(Waiting Room) 설정.
 *
 * @param enabled           대기열 사용 여부. false 면 모든 요청이 즉시 통과(기존 동작과 동일).
 * @param promoteBatchSize  1회 승격 시 입장시킬 인원. 하류(DB·토스)가 감당 가능한 처리량으로 잡는다.
 * @param promoteIntervalMs 승격 스케줄러 실행 주기(ms).
 * @param activeTtlSeconds  입장권(active 토큰) 유효시간. 만료되면 자리가 자동 반납된다.
 */
@ConfigurationProperties("app.waiting-queue")
public record WaitingQueueProperties(
        boolean enabled,
        int promoteBatchSize,
        long promoteIntervalMs,
        long activeTtlSeconds
) {
    public WaitingQueueProperties {
        if (promoteBatchSize <= 0) promoteBatchSize = 100;
        if (promoteIntervalMs <= 0) promoteIntervalMs = 1000;
        if (activeTtlSeconds <= 0) activeTtlSeconds = 300;
    }
}
