import apiClient from './api';
import type { ApiResponse } from './types';

/**
 * 결제 · 결제 대기열 API 클라이언트.
 *
 * 백엔드는 결제 생성 시 `DEVMATCH-XXXXXXXX` 형식의 orderId 를 직접 발급한다.
 * 프런트가 임의로 만든 값을 쓰면 승인 시점에 서버가 그 주문을 찾지 못하고,
 * 서버의 멱등성 보장(분산 락 · 상태 체크 · 토스 Idempotency-Key)도 걸리지 않는다.
 * 반드시 createPayment 가 돌려준 orderId 를 토스 위젯에 넘겨야 한다.
 */

export type PaymentStatus = 'PENDING' | 'CONFIRMED' | 'FAILED' | 'CANCELLED';

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
  courseType: string;
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
