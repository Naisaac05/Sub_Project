'use client';

import { useEffect, useMemo, useState } from 'react';
import { getEnrollmentPlans, type EnrollmentPlanPresentation } from './course-catalog';
import {
  DEFAULT_ENROLLMENT_MONTHS,
  fetchPlanPricing,
  type PlanPricing,
} from './payment';

export interface EnrollmentPlanCard extends EnrollmentPlanPresentation {
  /** 서버가 계산한 가격. 아직 못 받아왔으면 null — 이때는 금액을 추측해 표시하지 않는다. */
  pricing: PlanPricing | null;
}

interface EnrollmentPlansState {
  plans: EnrollmentPlanCard[];
  isLoading: boolean;
  /** 가격 조회 실패 — 금액 자리에 "문의" 를 노출하고 결제 진행은 막아야 한다. */
  error: string | null;
}

/**
 * 표시 정보(차수/마감)는 프론트에서, **금액은 백엔드에서** 가져와 합친 플랜 카드 목록.
 *
 * 금액을 프론트에 하드코딩하지 않는 것이 핵심이다. 예전에는 카드 가격과 백엔드
 * 계산식이 각자 따로 있어서 표시가와 청구액이 약 111만원 차이 났다.
 */
export function useEnrollmentPlans(months: number = DEFAULT_ENROLLMENT_MONTHS): EnrollmentPlansState {
  const presentation = useMemo(() => getEnrollmentPlans(), []);
  const [pricing, setPricing] = useState<PlanPricing[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setError(null);
      try {
        const res = await fetchPlanPricing(months);
        if (!cancelled) {
          setPricing(res.data);
        }
      } catch {
        if (!cancelled) {
          setPricing(null);
          setError('가격 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
        }
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [months]);

  const plans = useMemo<EnrollmentPlanCard[]>(
    () =>
      presentation.map((plan) => ({
        ...plan,
        pricing: pricing?.find((item) => item.plan === plan.id) ?? null,
      })),
    [presentation, pricing]
  );

  return { plans, isLoading: pricing === null && error === null, error };
}

export function formatWon(amount: number) {
  return `${amount.toLocaleString('ko-KR')}원`;
}
