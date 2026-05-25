import apiClient from './api';
import { getAccessToken } from './token';
import type {
  AiReviewSessionResponse,
  AiReviewSummaryResponse,
  AiReviewSubmitResponse,
  ApiResponse,
} from './types';

const AI_REVIEW_TIMEOUT_MS = 45000;

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
  mode: 'CHECK_ANSWER' | 'FREE_QUESTION' | 'NEXT_QUESTION' = 'CHECK_ANSWER',
  questionId?: number | null,
  clientRequestId?: string | null
): Promise<ApiResponse<AiReviewSubmitResponse>> {
  const res = await apiClient.post<ApiResponse<AiReviewSubmitResponse>>(
    `/ai-review/sessions/${sessionId}/messages`,
    { answer, mode, questionId, clientRequestId },
    { timeout: AI_REVIEW_TIMEOUT_MS }
  );
  return res.data;
}

export async function submitAiReviewAnswerStream(
  sessionId: number,
  answer: string,
  mode: 'CHECK_ANSWER' | 'FREE_QUESTION' | 'NEXT_QUESTION' = 'CHECK_ANSWER',
  questionId?: number | null,
  clientRequestId?: string | null,
  signal?: AbortSignal
): Promise<Response> {
  const token = getAccessToken();
  const url = `${process.env.NEXT_PUBLIC_API_BASE_URL || '/api'}/ai-review/sessions/${sessionId}/messages/stream`;
  return fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ answer, mode, questionId, clientRequestId }),
    signal
  });
}


export async function summarizeAiReviewQuestion(
  sessionId: number,
  questionId: number
): Promise<ApiResponse<AiReviewSummaryResponse>> {
  const res = await apiClient.post<ApiResponse<AiReviewSummaryResponse>>(
    `/ai-review/sessions/${sessionId}/questions/${questionId}/summary`,
    null,
    { timeout: AI_REVIEW_TIMEOUT_MS }
  );
  return res.data;
}

export async function summarizeAiReviewSession(
  sessionId: number
): Promise<ApiResponse<AiReviewSummaryResponse>> {
  const res = await apiClient.post<ApiResponse<AiReviewSummaryResponse>>(
    `/ai-review/sessions/${sessionId}/summary`,
    null,
    { timeout: AI_REVIEW_TIMEOUT_MS }
  );
  return res.data;
}
