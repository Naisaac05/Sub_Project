package com.devmatch.service.ai;

import org.slf4j.MDC;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Signal;
import reactor.util.context.Context;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

public class MdcReactiveBridge {

    private static final String MDC_CONTEXT_KEY = "MDC_CONTEXT_KEY";

    /**
     * 현재 메인 스레드의 MDC 컨텍스트 맵을 복사하여 Reactor Context에 캡처 및 영속화하는 헬퍼
     */
    public static Context captureCurrentMdc() {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (contextMap == null || contextMap.isEmpty()) {
            return Context.empty();
        }
        return Context.of(MDC_CONTEXT_KEY, contextMap);
    }

    /**
     * Reactor Context에 저장된 MDC 맵 복사본을 꺼내어 ThreadLocal MDC에 바인딩해주는 컨슈머 헬퍼
     */
    public static <T> Consumer<Signal<T>> bridgeHelper() {
        return signal -> {
            if (signal.getContextView().hasKey(MDC_CONTEXT_KEY)) {
                Map<String, String> contextMap = signal.getContextView().get(MDC_CONTEXT_KEY);
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
            }
        };
    }

    /**
     * Flux 파이프라인에 명시적으로 MDCContext를 브릿지 전송 및 정리하는 transform Operator
     */
    public static <T> Function<Flux<T>, Flux<T>> fluxBridge() {
        return flux -> flux
                .doOnEach(bridgeHelper())
                .doOnComplete(MdcReactiveBridge::clearMdc)
                .doOnError(err -> clearMdc())
                .doOnCancel(MdcReactiveBridge::clearMdc);
    }

    /**
     * Mono 파이프라인에 명시적으로 MDCContext를 브릿지 전송 및 정리하는 transform Operator
     */
    public static <T> Function<Mono<T>, Mono<T>> monoBridge() {
        return mono -> mono
                .doOnEach(bridgeHelper())
                .doOnSuccess(val -> clearMdc())
                .doOnError(err -> clearMdc())
                .doOnCancel(MdcReactiveBridge::clearMdc);
    }

    private static void clearMdc() {
        MDC.clear();
    }
}
