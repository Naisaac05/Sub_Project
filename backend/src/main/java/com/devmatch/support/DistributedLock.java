package com.devmatch.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis 기반 분산 락 (single-flight 패턴).
 *
 * <p>Redis 에는 "락"이라는 기능이 따로 없다. 원자적 {@code SET key value NX EX} 명령
 * 위에 얹은 <b>약속</b>일 뿐이다. 이 컴포넌트는 그 약속을 한 곳에 캡슐화한다.
 *
 * <ul>
 *   <li><b>획득</b>: {@code SET NX}(키가 없을 때만 세팅) + TTL(락 홀더가 죽어도 자동 해제 → 데드락 방지).</li>
 *   <li><b>해제</b>: <b>내가 잡은 락(owner 일치)일 때만</b> 삭제. Lua 스크립트로 get→del 을 원자적으로
 *       처리해, "내 락이 TTL 로 만료된 뒤 남이 잡은 락"을 실수로 지우는 사고를 막는다.</li>
 * </ul>
 *
 * <p>주의: 분산 락은 "동시 실행"만 막는다. "이미 끝난 작업의 재시도"는 막지 못하므로,
 * 멱등성은 별도의 영속 기록(예: Payment.status + DB 유니크 제약)으로 보장해야 한다.
 */
@Component
@RequiredArgsConstructor
public class DistributedLock {

    private final StringRedisTemplate redis;

    /** owner 가 일치할 때만 삭제하는 compare-and-delete 스크립트. */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /**
     * 락 획득 시도. 성공하면 소유권 증명용 owner 토큰을 반환, 이미 잠겨 있으면 {@code null}.
     */
    public String tryLock(String key, Duration ttl) {
        String owner = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, owner, ttl);
        return Boolean.TRUE.equals(acquired) ? owner : null;
    }

    /**
     * 락 해제. {@link #tryLock} 이 돌려준 owner 를 넘겨야 하며, 현재 락 소유자가 나일 때만 삭제된다.
     */
    public void unlock(String key, String owner) {
        if (owner == null) {
            return;
        }
        redis.execute(UNLOCK_SCRIPT, List.of(key), owner);
    }
}
