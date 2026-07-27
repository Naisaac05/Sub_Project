import apiClient from './api';
import type { ApiResponse } from './types';

/**
 * 결제 · 가격 조회 · 결제 대기열 API 클라이언트.
 *
 * 두 가지 원칙이 있다.
 *
 * 1. **orderId 는 서버가 발급한다.** 백엔드는 결제 생성 시 `DEVMATCH-XXXXXXXX` 를 만들고,
 *    그 값을 기준으로 분산 락(`pay:confirm:lock:{orderId}`)과 토스 Idempotency-Key 를 건다.
 *    프런트가 임의 값을 쓰면 승인 시점에 서버가 그 주문을 찾지 못하고 멱등성 보장도 걸리지 않는다.
 * 2. **금액도 서버가 계산한다.** 화면에 가격을 하드코딩하면 백엔드 정책이 바뀌는 순간
 *    "카드에 보이는 금액 ≠ 실제 청구 금액"이 된다. 승인 시 서버가 금액 일치를 검증하므로
 *    표시·위젯·승인이 모두 같은 값을 써야 한다.
 */

export type PaymentStatus = 'PENDING' | 'CONFIRMED' | 'FAILED' | 'CANCELLED';

export type EnrollmentPlanId = 'IMMEDIATE' | 'EARLY_BIRD_1' | 'EARLY_BIRD_2';

/** 기본 수강 기간 — 플랜 카드는 이 개월 수 기준 가격을 보여준다. */
export const DEFAULT_ENROLLMENT_MONTHS = 4;

/**
 * 백엔드 가격 정책 엔진(PaymentService)이 계산한 플랜 가격.
 * 프런트엔드는 이 값을 **계산 없이 그대로** 표시한다.
 */
export interface PlanPricing {
  plan: EnrollmentPlanId;
  unitPrice: number;
  monthsBundled: number;
  renewalCount: number;
  /** 할인 전 정가 — 카드의 취소선 가격 */
  rawTotal: number;
  bundleDiscount: number;
  planDiscount: number;
  discountAmount: number;
  /** 실제 청구 금액 */
  finalAmount: number;
}

export interface PaymentResponse {
  id: number;
  userId: number;
  applicationId: number;
  matchingId: number | null;
  orderId: string;
  paymentKey: string | null;
  amount: number;
  status: PaymentStatus;
  courseType: string | null;
  monthsBundled: number;
  renewalCount: number;
  discountApplied: number;
  installmentMonths: number;
  cancelReason: string | null;
  createdAt: string;
}

export interface PaymentCreateRequest {
  applicationId: number;
  courseType: EnrollmentPlanId;
  monthsBundled?: number;
  installmentMonths?: number;
}

export interface PaymentConfirmRequest {
  paymentKey: string;
  orderId: string;
  amount: number;
}

/** 대기열 상태. active 면 입장 완료, 아니면 rank(0-based, 내 앞 인원)와 total 이 온다. */
export interface QueueStatus {
  active: boolean;
  rank: number | null;
  total: number | null;
}

// ===== 가격 =====

/** 플랜별 가격 조회. 비로그인도 호출 가능하며, 로그인 시 본인 연장 회차가 반영된다. */
export async function fetchPlanPricing(
  months: number = DEFAULT_ENROLLMENT_MONTHS,
): Promise<ApiResponse<PlanPricing[]>> {
  const res = await apiClient.get<ApiResponse<PlanPricing[]>>('/payments/pricing', {
    params: { months },
  });
  return res.data;
}

// ===== 결제 =====

/** 결제 생성 — 서버가 orderId 와 최종 금액(할인 반영)을 계산해 돌려준다. */
export async function createPayment(
  data: PaymentCreateRequest,
): Promise<ApiResponse<PaymentResponse>> {
  const res = await apiClient.post<ApiResponse<PaymentResponse>>('/payments', data);
  return res.data;
}

/**
 * 결제 승인 — 토스 결제창 성공 리다이렉트 이후 호출한다.
 * 서버에서 분산 락 + 멱등성 체크를 거치므로, 같은 요청을 다시 보내도
 * 중복 승인 없이 기존 결과가 그대로 돌아온다.
 */
export async function confirmPayment(
  data: PaymentConfirmRequest,
): Promise<ApiResponse<PaymentResponse>> {
  const res = await apiClient.post<ApiResponse<PaymentResponse>>('/payments/confirm', data);
  return res.data;
}

/** 내 결제 목록 */
export async function getMyPayments(): Promise<ApiResponse<PaymentResponse[]>> {
  const res = await apiClient.get<ApiResponse<PaymentResponse[]>>('/payments');
  return res.data;
}

// ===== 대기열 =====

/** 대기열 진입. 대기열이 꺼져 있거나 이미 입장 상태면 active=true 로 즉시 통과한다. */
export async function enterQueue(): Promise<ApiResponse<QueueStatus>> {
  const res = await apiClient.post<ApiResponse<QueueStatus>>('/payments/queue/enter');
  return res.data;
}

/** 현재 순번 조회 — 대기 화면에서 주기적으로 폴링한다. */
export async function getQueueStatus(): Promise<ApiResponse<QueueStatus>> {
  const res = await apiClient.get<ApiResponse<QueueStatus>>('/payments/queue/status');
  return res.data;
}
