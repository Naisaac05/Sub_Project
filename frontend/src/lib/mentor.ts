import apiClient from './api';
import type { ApiResponse, MentorApplyRequest, MentorProfileResponse } from './types';

export async function applyAsMentor(data: MentorApplyRequest): Promise<MentorProfileResponse> {
  const res = await apiClient.post<ApiResponse<MentorProfileResponse>>('/mentor/apply', data);
  return res.data.data;
}

export async function getMyMentorProfile(): Promise<MentorProfileResponse | null> {
  try {
    const res = await apiClient.get<ApiResponse<MentorProfileResponse>>('/mentor/me');
    return res.data.data;
  } catch (err: any) {
    if (err.response?.status === 404) return null;
    throw err;
  }
}
