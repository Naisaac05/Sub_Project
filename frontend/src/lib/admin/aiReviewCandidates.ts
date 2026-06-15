import apiClient from '@/lib/api';
import type { ApiResponse } from '@/lib/types';

export type AiReviewCandidate = {
  candidateId: string;
  term: string;
  category: string;
  source: string;
  aliases: string[];
  definition: string;
  definitionDraft: string;
  definitionStatus: string;
  approved: boolean;
  humanReviewStatus: string;
  criticRiskLevel: string;
  criticRecommendation: string;
  criticFeedback: {
    risk_level?: string;
    approval_recommendation?: string;
    critic_feedback?: string;
    suggested_revision?: string;
  };
  duplicateStatus: string;
  duplicateConceptIds: string[];
  duplicateReason: string;
  sourceQuestionIds: string[];
  sourceQuestion: string;
  resolvedQuery: string;
  route: string;
  confidenceScore: number | null;
  needsReviewReason: string;
  createdAt: string;
  rejectedReason: string;
  reviewedAt: string;
  reviewer: string;
};

export type ReviewCandidateRequest = {
  candidateId?: string;
  term: string;
  category?: string;
  action: 'approve' | 'reject' | 'hold';
  definition?: string;
  rejectedReason?: string;
  reviewer?: string;
};

export type AiReviewCandidateV2Status = 'PENDING' | 'APPROVED' | 'REJECTED' | 'MERGED';
export type AiReviewCandidateWorkflowPhase =
  | 'CAPTURED'
  | 'DRAFTED'
  | 'HUMAN_REVIEW'
  | 'PUBLISH_FAILED'
  | 'APPROVED'
  | 'REJECTED'
  | 'MERGED';
export type AiReviewCandidateV2Source = 'COURSE' | 'AUTO' | 'MANUAL';
export type AiReviewCandidateV2Action = 'START_REVIEW' | 'APPROVE' | 'EDIT_AND_APPROVE' | 'REJECT' | 'MERGE';

export type AiReviewCandidateV2 = {
  id: number;
  externalCandidateId: string | null;
  term: string;
  category: string;
  source: AiReviewCandidateV2Source;
  status: AiReviewCandidateV2Status;
  workflowPhase: AiReviewCandidateWorkflowPhase;
  definition: string | null;
  definitionDraft: string | null;
  reviewerEditedAnswer: string | null;
  rejectedReason: string | null;
  mergedIntoId: number | null;
  sourceQuestion: string | null;
  resolvedQuery: string | null;
  route: string | null;
  confidenceScore: number | null;
  needsReviewReason: string | null;
  publishError: string | null;
  publishedCardId: string | null;
  publishedCardPath: string | null;
  reviewer: string | null;
  reviewedAt: string | null;
  retentionUntil: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type ReviewCandidateV2Request = {
  action: AiReviewCandidateV2Action;
  definition?: string;
  reviewerEditedAnswer?: string;
  rejectedReason?: string;
  mergedIntoId?: number;
  reviewer?: string;
  retentionDays?: number;
};

export async function fetchAiReviewCandidates(): Promise<AiReviewCandidate[]> {
  const res = await apiClient.get<ApiResponse<AiReviewCandidate[]>>('/admin/ai-review/candidates');
  return res.data.data ?? [];
}

export async function reviewAiCandidate(req: ReviewCandidateRequest): Promise<AiReviewCandidate> {
  const res = await apiClient.post<ApiResponse<AiReviewCandidate>>('/admin/ai-review/candidates/review', req);
  return res.data.data!;
}

export async function fetchAiReviewCandidatesV2(): Promise<AiReviewCandidateV2[]> {
  const res = await apiClient.get<ApiResponse<AiReviewCandidateV2[]>>('/admin/ai-review/candidates/v2');
  return res.data.data ?? [];
}

export async function importAiReviewCandidatesV2(): Promise<number> {
  const res = await apiClient.post<ApiResponse<{ imported: number }>>('/admin/ai-review/candidates/v2/import-jsonl');
  return res.data.data?.imported ?? 0;
}

export async function reviewAiCandidateV2(id: number, req: ReviewCandidateV2Request): Promise<AiReviewCandidateV2> {
  const res = await apiClient.patch<ApiResponse<AiReviewCandidateV2>>(`/admin/ai-review/candidates/v2/${id}/review`, req);
  return res.data.data!;
}
