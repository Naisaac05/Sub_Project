package com.devmatch.exception;

/**
 * 동일 주문에 대한 결제 승인이 이미 처리 중일 때(분산 락 획득 실패) 발생.
 * 보통 버튼 더블클릭·네트워크 재시도로 인한 동시 요청이며, 클라이언트는 잠시 후 재시도하면 된다.
 */
public class PaymentInProgressException extends RuntimeException {
    public PaymentInProgressException(String message) {
        super(message);
    }
}
