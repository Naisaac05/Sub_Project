package com.devmatch.entity;

/**
 * 관리자 행위 유형. AdminAuditLog.actionType 에서 사용한다.
 *
 * 새 값 추가 시 반드시 스펙 문서
 * (docs/superpowers/specs/2026-04-22-admin-console-common-design.md) 업데이트.
 */
public enum AdminActionType {
    // Phase II Common
    USER_ROLE_CHANGE,
    PAYMENT_REFUND,
    POST_DELETE,
    COMMENT_DELETE,
    MENTOR_APPROVE,
    MENTOR_REJECT,

    // Phase II Feature 1 (회원 관리)
    USER_DEACTIVATE,
    USER_REACTIVATE,
    USER_DELETE,
    USER_PASSWORD_RESET,
    USER_MENTOR_SWAP,
    ADMIN_CREATE,

    // Phase II Feature 5 (멘토 교체 신청)
    MENTOR_CHANGE_APPROVE,
    MENTOR_CHANGE_REJECT
}
