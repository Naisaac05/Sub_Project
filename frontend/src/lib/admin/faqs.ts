import apiClient from '../api';
import type { ApiResponse } from '../types';
import type { Faq, FaqCategory } from '../faqs';

export interface FaqCreateRequest {
  category: FaqCategory;
  question: string;
  answer: string;
  published?: boolean;
}

export interface FaqUpdateRequest {
  category?: FaqCategory;
  question?: string;
  answer?: string;
  orderIndex?: number;
  published?: boolean;
}

export async function fetchAdminFaqs(): Promise<Faq[]> {
  const res = await apiClient.get<ApiResponse<Faq[]>>('/admin/faqs');
  return res.data.data;
}

export async function createFaq(req: FaqCreateRequest): Promise<Faq> {
  const res = await apiClient.post<ApiResponse<Faq>>('/admin/faqs', req);
  return res.data.data;
}

export async function updateFaq(id: number, req: FaqUpdateRequest): Promise<Faq> {
  const res = await apiClient.put<ApiResponse<Faq>>(`/admin/faqs/${id}`, req);
  return res.data.data;
}

export async function deleteFaq(id: number): Promise<void> {
  await apiClient.delete(`/admin/faqs/${id}`);
}
