import apiClient from '../api';
import type { ApiResponse, PageResponse } from '../types';

export type { PageResponse };

export type MentorChangeRequestStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'CANCELLED';

export interface AdminMentorChangeListItem {
  id: number;
  menteeId: number;
  menteeName: string;
  menteeEmail: string | null;
  currentMentorId: number;
  currentMentorName: string;
  reasonPreview: string;
  status: MentorChangeRequestStatus;
  createdAt: string;
  respondedAt: string | null;
}

export interface AdminMentorChangeDetail {
  id: number;
  menteeId: number;
  menteeName: string;
  menteeEmail: string | null;
  currentMatchingId: number;
  currentCategory: string | null;
  currentMentorId: number;
  currentMentorName: string;
  reason: string;
  status: MentorChangeRequestStatus;
  newMentorId: number | null;
  newMentorName: string | null;
  rejectReason: string | null;
  decidedByAdminId: number | null;
  createdAt: string;
  respondedAt: string | null;
}

export interface CandidateMentor {
  userId: number;
  name: string;
  email: string | null;
  activeMenteeCount: number;
}

export interface ListAdminMentorChangeParams {
  page?: number;
  size?: number;
  status?: MentorChangeRequestStatus;
}

export async function listAdminMentorChangeRequests(
  params: ListAdminMentorChangeParams,
): Promise<PageResponse<AdminMentorChangeListItem>> {
  const res = await apiClient.get<ApiResponse<PageResponse<AdminMentorChangeListItem>>>(
    '/admin/mentor-change-requests',
    { params },
  );
  return res.data.data!;
}

export async function getAdminMentorChangeRequest(
  id: number,
): Promise<AdminMentorChangeDetail> {
  const res = await apiClient.get<ApiResponse<AdminMentorChangeDetail>>(
    `/admin/mentor-change-requests/${id}`,
  );
  return res.data.data!;
}

export async function listCandidateMentors(
  id: number,
  keyword: string,
  page: number,
  size = 10,
  sameCategoryOnly = true,
): Promise<PageResponse<CandidateMentor>> {
  const res = await apiClient.get<ApiResponse<PageResponse<CandidateMentor>>>(
    `/admin/mentor-change-requests/${id}/candidate-mentors`,
    { params: { keyword, page, size, sameCategoryOnly } },
  );
  return res.data.data!;
}

export async function approveMentorChangeRequest(
  id: number,
  newMentorUserId: number,
): Promise<AdminMentorChangeDetail> {
  const res = await apiClient.post<ApiResponse<AdminMentorChangeDetail>>(
    `/admin/mentor-change-requests/${id}/approve`,
    { newMentorUserId },
  );
  return res.data.data!;
}

export async function rejectMentorChangeRequest(
  id: number,
  rejectReason: string,
): Promise<AdminMentorChangeDetail> {
  const res = await apiClient.post<ApiResponse<AdminMentorChangeDetail>>(
    `/admin/mentor-change-requests/${id}/reject`,
    { rejectReason },
  );
  return res.data.data!;
}
