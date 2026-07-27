package com.devmatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 대기열 승격 스케줄러 — "입장 관리자".
 *
 * <p>주기적으로 대기열 앞에서 정해진 인원만 꺼내 입장시킨다. 이 주기와 배치 크기의 곱이
 * 시스템이 하류(DB·PG)로 흘려보내는 초당 처리량이 되며, 전체 부하는 이 두 값으로 조절한다.
 *
 * <p>{@code app.waiting-queue.enabled=true} 일 때만 빈이 등록된다.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.waiting-queue", name = "enabled", havingValue = "true")
public class WaitingQueuePromoter {

    private final WaitingQueueService waitingQueueService;

    @Scheduled(fixedDelayString = "${app.waiting-queue.promote-interval-ms:1000}")
    public void promote() {
        waitingQueueService.promote();
    }
}
