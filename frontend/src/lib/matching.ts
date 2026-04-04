import apiClient from './api';
import type {
  ApiResponse,
  MentorRecommendResponse,
  MatchingRequest,
  MatchingResponse,
  MatchingAcceptRequest,
} from './types';

/** 테스트 결과 기반 멘토 추천 */
export async function recommendMentors(category: string): Promise<ApiResponse<MentorRecommendResponse[]>> {
  const res = await apiClient.get<ApiResponse<MentorRecommendResponse[]>>('/matching/recommend', {
    params: { category },
  });
  return res.data;
}

/** 매칭 신청 */
export async function requestMatching(data: MatchingRequest): Promise<ApiResponse<MatchingResponse>> {
  const res = await apiClient.post<ApiResponse<MatchingResponse>>('/matching/request', data);
  return res.data;
}

/** 매칭 수락/거절 (멘토 전용) */
export async function acceptMatching(matchingId: number, data: MatchingAcceptRequest): Promise<ApiResponse<MatchingResponse>> {
  const res = await apiClient.put<ApiResponse<MatchingResponse>>(`/matching/${matchingId}/accept`, data);
  return res.data;
}

/** 멘티 입장 매칭 내역 */
export async function getMyMatchingsAsMentee(): Promise<ApiResponse<MatchingResponse[]>> {
  const res = await apiClient.get<ApiResponse<MatchingResponse[]>>('/matching/mentee');
  return res.data;
}

/** 멘토 입장 매칭 요청 목록 */
export async function getMyMatchingsAsMentor(): Promise<ApiResponse<MatchingResponse[]>> {
  const res = await apiClient.get<ApiResponse<MatchingResponse[]>>('/matching/mentor');
  return res.data;
}
