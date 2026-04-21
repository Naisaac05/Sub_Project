import apiClient from './api';
import type {
  ApiResponse,
  MentorProfileResponse,
  PageResponse,
} from './types';

export type AdminMentorStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface ListMentorApplicationsParams {
  status?: AdminMentorStatus;
  page?: number; // 0-indexed
  size?: number; // default backend=20
}

/**
 * 관리자: 멘토 신청 목록 조회 (페이징).
 */
export async function listMentorApplications(
  params: ListMentorApplicationsParams = {},
): Promise<PageResponse<MentorProfileResponse>> {
  const res = await apiClient.get<
    ApiResponse<PageResponse<MentorProfileResponse>>
  >('/admin/mentor', {
    params: {
      ...(params.status ? { status: params.status } : {}),
      ...(params.page !== undefined ? { page: params.page } : {}),
      ...(params.size !== undefined ? { size: params.size } : {}),
    },
  });
  return res.data.data;
}

/**
 * 관리자: 멘토 신청 단건 조회. 404 시 rejected.
 */
export async function getMentorApplication(
  profileId: number,
): Promise<MentorProfileResponse> {
  const res = await apiClient.get<ApiResponse<MentorProfileResponse>>(
    `/admin/mentor/${profileId}`,
  );
  return res.data.data;
}

/**
 * 관리자: 멘토 신청 승인. PENDING 이 아니면 409.
 */
export async function approveMentor(
  profileId: number,
): Promise<MentorProfileResponse> {
  const res = await apiClient.post<ApiResponse<MentorProfileResponse>>(
    `/admin/mentor/${profileId}/approve`,
  );
  return res.data.data;
}

/**
 * 관리자: 멘토 신청 반려. reason 은 10~500자 필수.
 * - 400: 사유 검증 실패
 * - 409: 이미 APPROVED/REJECTED 처리됨
 */
export async function rejectMentor(
  profileId: number,
  reason: string,
): Promise<MentorProfileResponse> {
  const res = await apiClient.post<ApiResponse<MentorProfileResponse>>(
    `/admin/mentor/${profileId}/reject`,
    { reason },
  );
  return res.data.data;
}
