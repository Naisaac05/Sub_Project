package com.devmatch.entity;

/**
 * 관리자 행위 유형. AdminAuditLog.actionType 에서 사용한다.
 * Phase II 에서 실제 기록되는 값: USER_ROLE_CHANGE, PAYMENT_REFUND, POST_DELETE,
 * COMMENT_DELETE, MENTOR_APPROVE, MENTOR_REJECT.
 *
 * 새 값 추가 시 반드시 스펙 문서
 * (docs/superpowers/specs/2026-04-22-admin-console-common-design.md) 업데이트.
 */
public enum AdminActionType {
    USER_ROLE_CHANGE,
    PAYMENT_REFUND,
    POST_DELETE,
    COMMENT_DELETE,
    MENTOR_APPROVE,
    MENTOR_REJECT
}
