package com.devmatch.entity;

/**
 * 회원 lifecycle 상태.
 *
 * - ACTIVE: 정상 (기본값)
 * - DEACTIVATED: 관리자 비활성화 (로그인·매칭 불가, 데이터 보존, 재활성화 가능)
 * - DELETED: 관리자 영구 삭제 (UI 비가역. 표시 시 "탈퇴한 회원" 으로 마스킹)
 */
public enum UserStatus {
    ACTIVE,
    DEACTIVATED,
    DELETED
}
