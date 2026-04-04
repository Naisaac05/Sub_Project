import apiClient from './api';
import type {
  ApiResponse,
  TestListResponse,
  TestDetailResponse,
  TestSubmitRequest,
  TestResultResponse,
} from './types';

/** 테스트 목록 조회 (category null이면 전체) */
export async function getTests(category?: string): Promise<ApiResponse<TestListResponse[]>> {
  const params = category ? { category } : {};
  const res = await apiClient.get<ApiResponse<TestListResponse[]>>('/tests', { params });
  return res.data;
}

/** 테스트 상세 + 문제 목록 (정답 미포함) */
export async function getTestDetail(testId: number): Promise<ApiResponse<TestDetailResponse>> {
  const res = await apiClient.get<ApiResponse<TestDetailResponse>>(`/tests/${testId}`);
  return res.data;
}

/** 답안 제출 + 자동 채점 */
export async function submitTest(testId: number, data: TestSubmitRequest): Promise<ApiResponse<TestResultResponse>> {
  const res = await apiClient.post<ApiResponse<TestResultResponse>>(`/tests/${testId}/submit`, data);
  return res.data;
}

/** 내 테스트 결과 목록 */
export async function getMyResults(): Promise<ApiResponse<TestResultResponse[]>> {
  const res = await apiClient.get<ApiResponse<TestResultResponse[]>>('/tests/results');
  return res.data;
}
