package com.devmatch.entity;

/**
 * FAQ 카테고리. 5개 고정값.
 *
 * 새 값 추가 시 반드시 함께 업데이트:
 * - frontend/src/lib/faqs.ts CATEGORY_LABEL 매핑
 * - frontend/src/app/admin/faqs/page.tsx 카테고리 필터 탭 (CATEGORY_LABEL 자동 반영됨)
 * - DataInitializer 시드 데이터 (필요 시)
 */
public enum FaqCategory {
    SERVICE_INTRO,
    TEST,
    MENTORING,
    PAYMENT,
    MENTOR_APPLY
}
