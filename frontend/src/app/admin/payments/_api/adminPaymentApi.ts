import apiClient from '@/lib/api';
import type { ApiResponse } from '@/lib/types';
import type { AdminPaymentListItem, AdminPaymentSummary, AdminPaymentDetail, PaymentStatus } from '../_types';

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ListParams {
  status?: Exclude<PaymentStatus, never> | string;
  q?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export async function listPayments(params: ListParams): Promise<PageResponse<AdminPaymentListItem>> {
  // strip 'ALL' sentinel
  const apiParams: Record<string, string | number> = {};
  if (params.status && params.status !== 'ALL') apiParams.status = params.status;
  if (params.q) apiParams.q = params.q;
  if (params.from) apiParams.from = params.from;
  if (params.to) apiParams.to = params.to;
  if (params.page != null) apiParams.page = params.page;
  if (params.size != null) apiParams.size = params.size;
  const res = await apiClient.get<ApiResponse<PageResponse<AdminPaymentListItem>>>('/admin/payments', { params: apiParams });
  return res.data.data!;
}

export async function getPaymentSummary(from?: string, to?: string): Promise<AdminPaymentSummary> {
  const params: Record<string, string> = {};
  if (from) params.from = from;
  if (to) params.to = to;
  const res = await apiClient.get<ApiResponse<AdminPaymentSummary>>('/admin/payments/summary', { params });
  return res.data.data!;
}

export async function getPaymentDetail(id: number): Promise<AdminPaymentDetail> {
  const res = await apiClient.get<ApiResponse<AdminPaymentDetail>>(`/admin/payments/${id}`);
  return res.data.data!;
}

export async function refundPayment(id: number, reason: string): Promise<AdminPaymentDetail> {
  const res = await apiClient.post<ApiResponse<AdminPaymentDetail>>(`/admin/payments/${id}/refund`, { reason });
  return res.data.data!;
}
