package com.devmatch.exception;

/**
 * 대기열 입장권 없이 결제를 시도했을 때 발생. 클라이언트는 대기열 화면으로 돌아가야 한다.
 */
public class QueueNotAdmittedException extends RuntimeException {
    public QueueNotAdmittedException(String message) {
        super(message);
    }
}
