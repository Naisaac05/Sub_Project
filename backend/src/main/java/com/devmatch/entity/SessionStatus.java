package com.devmatch.entity;

public enum SessionStatus {
    PENDING,     // 승인 대기 (멘티 예약 직후, 멘토 승인 전)
    SCHEDULED,   // 예정됨
    COMPLETED,   // 완료됨
    CANCELLED    // 취소됨
}
