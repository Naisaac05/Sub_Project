import apiClient from './api';
import type {
  AiReviewSessionResponse,
  AiReviewSubmitResponse,
  ApiResponse,
} from './types';

const AI_REVIEW_TIMEOUT_MS = 60000;

export async function startAiReview(testResultId: number): Promise<ApiResponse<AiReviewSessionResponse>> {
  const res = await apiClient.post<ApiResponse<AiReviewSessionResponse>>(
    `/ai-review/test-results/${testResultId}/start`,
    null,
    { timeout: AI_REVIEW_TIMEOUT_MS }
  );
  return res.data;
}

export async function getAiReviewSession(sessionId: number): Promise<ApiResponse<AiReviewSessionResponse>> {
  const res = await apiClient.get<ApiResponse<AiReviewSessionResponse>>(
    `/ai-review/sessions/${sessionId}`,
    { timeout: AI_REVIEW_TIMEOUT_MS }
  );
  return res.data;
}

export async function submitAiReviewAnswer(
  sessionId: number,
  answer: string,
  mode: 'CHECK_ANSWER' | 'FREE_QUESTION' | 'NEXT_QUESTION' = 'CHECK_ANSWER'
): Promise<ApiResponse<AiReviewSubmitResponse>> {
  const res = await apiClient.post<ApiResponse<AiReviewSubmitResponse>>(
    `/ai-review/sessions/${sessionId}/messages`,
    { answer, mode },
    { timeout: AI_REVIEW_TIMEOUT_MS }
  );
  return res.data;
}
