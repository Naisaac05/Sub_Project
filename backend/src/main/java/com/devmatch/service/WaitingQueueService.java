package com.devmatch.service;

import com.devmatch.config.WaitingQueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 기반 결제 대기열(Waiting Room).
 *
 * <p>수백만 동시 요청을 하류(DB·PG)가 감당 가능한 속도로 <b>깎아내리는(traffic shaping)</b> 장치다.
 * 대기열은 "부하"를 조절할 뿐 "중복"은 막지 않는다 — 중복 결제 방어는 분산 락 + 상태 체크 +
 * DB 유니크 제약 + 토스 멱등키의 몫이다. 두 관심사를 섞지 않는다.
 *
 * <p><b>키 구조</b>
 * <pre>
 *   waiting:payment            ZSET    대기 줄 (member=userId, score=발급 순번)
 *   waiting:payment:counter    STRING  번호표 발급기 (INCR)
 *   waiting:payment:active:{u} STRING  입장권. TTL 로 자리 자동 반납
 * </pre>
 *
 * <p><b>ZADD NX 인 이유</b>: 대기 중 새로고침·재요청이 와도 이미 줄 서 있으면 점수를 갱신하지
 * 않는다. NX 가 없으면 매 요청마다 번호가 새로 매겨져 순번이 뒤로 밀리거나 앞으로 튄다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingQueueService {

    static final String QUEUE_KEY = "waiting:payment";
    static final String COUNTER_KEY = "waiting:payment:counter";
    static final String ACTIVE_KEY_PREFIX = "waiting:payment:active:";

    private final StringRedisTemplate redis;
    private final WaitingQueueProperties properties;

    /** 대기열 조회 결과. rank 는 0-based(내 앞 인원), 이미 입장한 경우 {@code active=true}. */
    public record QueueStatus(boolean active, Long rank, Long total) {
        public static QueueStatus admitted() {
            return new QueueStatus(true, null, null);
        }
    }

    /**
     * 대기열 진입. 이미 입장권이 있으면 즉시 통과하고, 아니면 번호표를 받아 줄에 선다.
     * 대기열이 비활성이면 항상 즉시 통과한다.
     */
    public QueueStatus enter(Long userId) {
        if (!properties.enabled() || isActive(userId)) {
            return QueueStatus.admitted();
        }
        String member = String.valueOf(userId);
        // 이미 줄 서 있으면 기존 순번 유지 (ZADD NX). 아니면 새 번호표 발급.
        Boolean added = redis.opsForZSet().addIfAbsent(QUEUE_KEY, member, nextTicket());
        if (Boolean.TRUE.equals(added)) {
            log.debug("[WaitingQueue] 신규 대기 등록 — userId={}", userId);
        }
        return status(userId);
    }

    /** 현재 상태 조회 (대기 화면 폴링용). */
    public QueueStatus status(Long userId) {
        if (!properties.enabled() || isActive(userId)) {
            return QueueStatus.admitted();
        }
        String member = String.valueOf(userId);
        Long rank = redis.opsForZSet().rank(QUEUE_KEY, member);
        Long total = redis.opsForZSet().zCard(QUEUE_KEY);
        return new QueueStatus(false, rank, total);
    }

    /** 입장권 보유 여부. 결제 API 진입 검사에 사용한다. */
    public boolean isActive(Long userId) {
        if (!properties.enabled()) {
            return true;
        }
        return Boolean.TRUE.equals(redis.hasKey(ACTIVE_KEY_PREFIX + userId));
    }

    /**
     * 대기열 앞에서 {@code promoteBatchSize} 명을 꺼내 입장권(TTL)을 발급한다.
     * 스케줄러가 주기적으로 호출하며, 이 배치 크기가 곧 하류로 흘려보내는 처리량이 된다.
     *
     * @return 이번에 승격된 인원 수
     */
    public int promote() {
        if (!properties.enabled()) {
            return 0;
        }
        Set<String> promoted = redis.opsForZSet().popMin(QUEUE_KEY, properties.promoteBatchSize())
                .stream()
                .map(tuple -> tuple.getValue())
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        for (String member : promoted) {
            redis.opsForValue().set(
                    ACTIVE_KEY_PREFIX + member, "1",
                    properties.activeTtlSeconds(), TimeUnit.SECONDS);
        }
        if (!promoted.isEmpty()) {
            log.info("[WaitingQueue] {}명 입장 승격 (TTL {}s)", promoted.size(), properties.activeTtlSeconds());
        }
        return promoted.size();
    }

    /** 입장권 반납 (결제 완료·취소 시 자리를 즉시 비운다). */
    public void release(Long userId) {
        redis.delete(ACTIVE_KEY_PREFIX + userId);
    }

    private long nextTicket() {
        Long ticket = redis.opsForValue().increment(COUNTER_KEY);
        return ticket != null ? ticket : System.currentTimeMillis();
    }
}
