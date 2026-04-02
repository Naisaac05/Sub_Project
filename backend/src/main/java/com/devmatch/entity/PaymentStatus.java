package com.devmatch.entity;

public enum PaymentStatus {
    PENDING,     // 결제 대기
    CONFIRMED,   // 결제 승인
    CANCELLED,   // 결제 취소
    FAILED       // 결제 실패
}
