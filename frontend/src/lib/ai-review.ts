import apiClient from './api';
import { getAccessToken } from './token';
import type {
  AiReviewSessionResponse,
  AiReviewSummaryResponse,
  AiReviewSubmitResponse,
  ApiResponse,
} from './types';

const AI_REVIEW_TIMEOUT_MS = 45000;

export class AiReviewRateLimitError extends Error {
  constructor(public readonly retryAfterSeconds: number | null) {
    super(rateLimitMessage(retryAfterSeconds));
    this.name = 'AiReviewRateLimitError';
  }
}

export function rateLimitMessage(retryAfterSeconds: number | null) {
  if (retryAfterSeconds && retryAfterSeconds > 0) {
    return `AI 요청이 많습니다. ${retryAfterSeconds}초 후 다시 시도해주세요.`;
  }
  return 'AI 요청이 많습니다. 잠시 후 다시 시도해주세요.';
}

export function isAiReviewRateLimitError(error: unknown): error is AiReviewRateLimitError {
  return error instanceof AiReviewRateLimitError;
}

export function retryAfterFromError(error: unknown): number | null {
  if (isAiReviewRateLimitError(error)) {
    return error.retryAfterSeconds;
  }
  const maybeAxiosError = error as {
    response?: { status?: number; headers?: Record<string, unknown> };
  };
  if (maybeAxiosError.response?.status !== 429) {
    return null;
  }
  const value = maybeAxiosError.response.headers?.['retry-after'];
  return parseRetryAfter(typeof value === 'string' || typeof value === 'number' ? String(value) : null);
}

function parseRetryAfter(value: string | null): number | null {
  if (!value) {
    return null;
  }
  const seconds = Number(value);
  if (Number.isFinite(seconds)) {
    return Math.max(1, Math.ceil(seconds));
  }
  const retryAt = Date.parse(value);
  if (Number.isNaN(retryAt)) {
    return null;
  }
  return Math.max(1, Math.ceil((retryAt - Date.now()) / 1000));
}

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
  questionId?: number | null
): Promise<ApiResponse<AiReviewSubmitResponse>> {
  const res = await apiClient.post<ApiResponse<AiReviewSubmitResponse>>(
    `/ai-review/sessions/${sessionId}/messages`,
    { answer, mode, questionId },
    { timeout: AI_REVIEW_TIMEOUT_MS }
  );
  return res.data;
}

export async function submitAiReviewAnswerStream(
  sessionId: number,
  answer: string,
  mode: 'CHECK_ANSWER' | 'FREE_QUESTION' | 'NEXT_QUESTION' = 'CHECK_ANSWER',
  questionId?: number | null,
  signal?: AbortSignal
): Promise<Response> {
  const token = getAccessToken();
  const url = `${process.env.NEXT_PUBLIC_API_BASE_URL || '/api'}/ai-review/sessions/${sessionId}/messages/stream`;
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'text/event-stream',
      ...(token ? { 'Authorization': `Bearer ${token}` } : {})
    },
    body: JSON.stringify({ answer, mode, questionId }),
    signal
  });
  if (response.status === 429) {
    throw new AiReviewRateLimitError(parseRetryAfter(response.headers.get('retry-after')));
  }
  return response;
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

// 🧪 테스트 전용: AI 리뷰 세션 초기화 API
// 제거 시 본 함수 + TestResetButton.tsx + page.tsx의 import/render 함께 삭제
// 백엔드는 환경변수 OFF 면 404 반환
export async function resetAiReviewSession(testResultId: number): Promise<void> {
  await apiClient.post(
    `/ai-review/test-results/${testResultId}/session/reset`,
    null,
    { timeout: AI_REVIEW_TIMEOUT_MS }
  );
}
