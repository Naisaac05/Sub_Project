import apiClient from './api';
import type { ApiResponse } from './types';

export const COMMUNITY_CATEGORIES = [
  '질문/답변',
  '학습 공유',
  '멘토링 후기',
  '취업/이직',
  '자유게시판',
] as const;

export type CommunityCategory = (typeof COMMUNITY_CATEGORIES)[number];

export interface CommunityPost {
  id: number;
  authorId: number;
  authorName: string;
  category: CommunityCategory;
  title: string;
  content: string;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  liked: boolean;
  createdAt: string;
}

export interface CommunityComment {
  id: number;
  authorId: number;
  authorName: string;
  content: string;
  createdAt: string;
}

type PageResponse<T> = {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
};

export async function getCommunityPosts() {
  const response = await apiClient.get<ApiResponse<PageResponse<CommunityPost>>>('/posts', {
    params: { size: 100 },
  });
  return response.data;
}

export async function getCommunityPost(postId: number) {
  const response = await apiClient.get<ApiResponse<CommunityPost>>(`/posts/${postId}`);
  return response.data;
}

export async function createCommunityPost(data: {
  category: CommunityCategory;
  title: string;
  content: string;
}) {
  const response = await apiClient.post<ApiResponse<CommunityPost>>('/posts', data);
  return response.data;
}

export async function updateCommunityPost(
  postId: number,
  data: { category: CommunityCategory; title: string; content: string }
) {
  const response = await apiClient.put<ApiResponse<CommunityPost>>(`/posts/${postId}`, data);
  return response.data;
}

export async function deleteCommunityPost(postId: number) {
  const response = await apiClient.delete<ApiResponse<void>>(`/posts/${postId}`);
  return response.data;
}

export async function toggleCommunityLike(postId: number) {
  const response = await apiClient.post<ApiResponse<CommunityPost>>(`/posts/${postId}/like`);
  return response.data;
}

export async function getCommunityComments(postId: number) {
  const response = await apiClient.get<ApiResponse<CommunityComment[]>>(`/posts/${postId}/comments`);
  return response.data;
}

export async function createCommunityComment(postId: number, content: string) {
  const response = await apiClient.post<ApiResponse<CommunityComment>>(`/posts/${postId}/comments`, { content });
  return response.data;
}

export async function deleteCommunityComment(postId: number, commentId: number) {
  const response = await apiClient.delete<ApiResponse<void>>(`/posts/${postId}/comments/${commentId}`);
  return response.data;
}
