package com.devmatch.entity;

public enum ApplicationStatus {
    DRAFT,
    SUBMITTED,
    ACCEPTED,
    REJECTED,
    PAYMENT_COMPLETED,
    PENDING_MENTOR_APPROVAL, // 하나의 멘토에게 할당되어 응답 대기 중
    MATCHING_FAILED // 배분 가능한 모든 멘토가 거절한 경우
}
