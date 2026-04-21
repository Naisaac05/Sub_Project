import apiClient from './api';
import type { ApiResponse, MentorProfileResponse } from './types';

export type AdminMentorStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/**
 * 관리자: 멘토 신청 목록 조회.
 * @param status - 필터. 미지정 시 전체 반환 (백엔드 기본 동작).
 */
export async function listMentorApplications(
  status?: AdminMentorStatus,
): Promise<MentorProfileResponse[]> {
  const params = status ? { status } : undefined;
  const res = await apiClient.get<ApiResponse<MentorProfileResponse[]>>(
    '/admin/mentor',
    { params },
  );
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
