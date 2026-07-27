package com.devmatch.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * 수강 신청 플랜.
 *
 * <p>플랜은 "언제 시작하느냐"만 다르고 커리큘럼은 동일하다. 미리 신청할수록
 * 월 단가에서 {@link #getMonthlyDiscount()} 만큼 얼리버드 할인을 받는다.
 *
 * <p>할인을 월 단위로 정의하는 이유: 묶음 개월 수가 달라져도 할인 폭이 비례해서
 * 커지므로, 1개월 결제에 4개월치 할인이 통째로 붙는 식의 왜곡이 생기지 않는다.
 */
public enum EnrollmentPlan {

    /** 즉시 시작 — 얼리버드 할인 없음 */
    IMMEDIATE(0),

    /** 다음 달 차수 — 월 2.5만원 할인 */
    EARLY_BIRD_1(25_000),

    /** 다다음 달 차수 — 월 5만원 할인 */
    EARLY_BIRD_2(50_000);

    private final int monthlyDiscount;

    EnrollmentPlan(int monthlyDiscount) {
        this.monthlyDiscount = monthlyDiscount;
    }

    /** 월 단가에서 깎이는 얼리버드 할인액(원) */
    public int getMonthlyDiscount() {
        return monthlyDiscount;
    }

    /** 묶음 개월 수에 비례한 총 얼리버드 할인액(원) */
    public int discountFor(int monthsBundled) {
        return monthlyDiscount * monthsBundled;
    }

    /**
     * 외부 입력(요청 본문 / DB 컬럼)에서 플랜을 파싱한다.
     * 알 수 없는 값이면 {@link Optional#empty()} — 호출자가 도메인 예외로 변환한다.
     */
    public static Optional<EnrollmentPlan> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(plan -> plan.name().equals(normalized))
                .findFirst();
    }
}
