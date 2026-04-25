import apiClient from './api';
import type { ApiResponse } from './types';

export type FaqCategory =
  | 'SERVICE_INTRO'
  | 'TEST'
  | 'MENTORING'
  | 'PAYMENT'
  | 'MENTOR_APPLY';

/** 카테고리 영문 enum → 한글 표시명. */
export const CATEGORY_LABEL: Record<FaqCategory, string> = {
  SERVICE_INTRO: '서비스 소개',
  TEST: '실력 테스트',
  MENTORING: '멘토링',
  PAYMENT: '결제/환불',
  MENTOR_APPLY: '멘토 지원',
};

/** 카테고리 표시 순서 (공개·어드민 페이지 모두 이 순서로 탭 노출). */
export const CATEGORY_ORDER: FaqCategory[] = [
  'SERVICE_INTRO', 'TEST', 'MENTORING', 'PAYMENT', 'MENTOR_APPLY',
];

export interface Faq {
  id: number;
  category: FaqCategory;
  question: string;
  answer: string;
  orderIndex: number;
  published: boolean;
  createdAt: string;
  updatedAt: string;
}

export async function fetchPublicFaqs(): Promise<Faq[]> {
  const res = await apiClient.get<ApiResponse<Faq[]>>('/faqs');
  return res.data.data;
}
