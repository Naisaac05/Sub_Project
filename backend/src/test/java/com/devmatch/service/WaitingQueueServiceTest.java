package com.devmatch.service;

import com.devmatch.config.WaitingQueueProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WaitingQueueServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ZSetOperations<String, String> zSetOps;
    @Mock private ValueOperations<String, String> valueOps;

    private WaitingQueueService service(boolean enabled) {
        when(redis.opsForZSet()).thenReturn(zSetOps);
        when(redis.opsForValue()).thenReturn(valueOps);
        return new WaitingQueueService(redis, new WaitingQueueProperties(enabled, 2, 1000, 300));
    }

    @Test
    void 대기열_비활성이면_항상_즉시_통과하고_redis를_건드리지_않는다() {
        WaitingQueueService svc = new WaitingQueueService(
                redis, new WaitingQueueProperties(false, 2, 1000, 300));

        assertThat(svc.isActive(1L)).isTrue();
        assertThat(svc.enter(1L).active()).isTrue();
        assertThat(svc.promote()).isZero();
        verifyNoInteractions(redis);
    }

    @Test
    void 입장권_보유자는_줄서지_않고_즉시_통과() {
        WaitingQueueService svc = service(true);
        when(redis.hasKey("waiting:payment:active:7")).thenReturn(true);

        assertThat(svc.enter(7L).active()).isTrue();
        verify(zSetOps, never()).addIfAbsent(anyString(), anyString(), anyLong());
    }

    @Test
    void 신규_진입은_번호표를_받아_ZADD_NX_로_줄서고_순번을_반환() {
        WaitingQueueService svc = service(true);
        when(redis.hasKey("waiting:payment:active:7")).thenReturn(false);
        when(valueOps.increment("waiting:payment:counter")).thenReturn(1001L);
        when(zSetOps.addIfAbsent(eq("waiting:payment"), eq("7"), eq(1001.0))).thenReturn(true);
        when(zSetOps.rank("waiting:payment", "7")).thenReturn(340L);
        when(zSetOps.zCard("waiting:payment")).thenReturn(12000L);

        WaitingQueueService.QueueStatus status = svc.enter(7L);

        assertThat(status.active()).isFalse();
        assertThat(status.rank()).isEqualTo(340L);
        assertThat(status.total()).isEqualTo(12000L);
        // ZADD NX — 이미 줄 서 있으면 점수를 갱신하지 않아야 한다
        verify(zSetOps).addIfAbsent("waiting:payment", "7", 1001.0);
    }

    @Test
    void promote_는_배치크기만큼_꺼내_TTL_입장권을_발급() {
        WaitingQueueService svc = service(true);
        Set<ZSetOperations.TypedTuple<String>> popped = new LinkedHashSet<>();
        popped.add(new org.springframework.data.redis.core.DefaultTypedTuple<>("7", 1001.0));
        popped.add(new org.springframework.data.redis.core.DefaultTypedTuple<>("8", 1002.0));
        when(zSetOps.popMin("waiting:payment", 2)).thenReturn(popped);

        int promoted = svc.promote();

        assertThat(promoted).isEqualTo(2);
        verify(valueOps).set(eq("waiting:payment:active:7"), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
        verify(valueOps).set(eq("waiting:payment:active:8"), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
    }

    @Test
    void release_는_입장권을_즉시_반납() {
        WaitingQueueService svc = service(true);
        svc.release(7L);
        verify(redis).delete("waiting:payment:active:7");
    }

    @Test
    void 잘못된_설정값은_안전한_기본값으로_보정된다() {
        WaitingQueueProperties props = new WaitingQueueProperties(true, 0, -5, 0);
        assertThat(props.promoteBatchSize()).isEqualTo(100);
        assertThat(props.promoteIntervalMs()).isEqualTo(1000);
        assertThat(props.activeTtlSeconds()).isEqualTo(300);
    }
}
