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
  imageUrl?: string | null;
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

const COMMUNITY_CATEGORY_CODES: Record<CommunityCategory, string> = {
  [COMMUNITY_CATEGORIES[0]]: 'question',
  [COMMUNITY_CATEGORIES[1]]: 'study',
  [COMMUNITY_CATEGORIES[2]]: 'review',
  [COMMUNITY_CATEGORIES[3]]: 'career',
  [COMMUNITY_CATEGORIES[4]]: 'free',
};

const COMMUNITY_CATEGORY_BY_CODE = Object.fromEntries(
  Object.entries(COMMUNITY_CATEGORY_CODES).map(([category, code]) => [code, category as CommunityCategory])
) as Record<string, CommunityCategory>;

type CommunityPostPayload = {
  category: CommunityCategory;
  title: string;
  content: string;
  imageUrl?: string;
};

export function normalizeCommunityCategory(category: string | null | undefined): CommunityCategory {
  if (!category) {
    return COMMUNITY_CATEGORIES[4];
  }

  const trimmed = category.trim();
  if ((COMMUNITY_CATEGORIES as readonly string[]).includes(trimmed)) {
    return trimmed as CommunityCategory;
  }

  return COMMUNITY_CATEGORY_BY_CODE[trimmed.toLowerCase()] ?? COMMUNITY_CATEGORIES[4];
}

function serializeCommunityPostPayload(data: CommunityPostPayload) {
  return {
    ...data,
    category: COMMUNITY_CATEGORY_CODES[normalizeCommunityCategory(data.category)],
  };
}

function mapCommunityPost(post: CommunityPost): CommunityPost {
  return {
    ...post,
    category: normalizeCommunityCategory(post.category),
  };
}

function mapCommunityPostPage(page: PageResponse<CommunityPost>): PageResponse<CommunityPost> {
  return {
    ...page,
    content: page.content.map(mapCommunityPost),
  };
}

export async function getCommunityPosts() {
  const response = await apiClient.get<ApiResponse<PageResponse<CommunityPost>>>('/posts', {
    params: { size: 100 },
  });
  return {
    ...response.data,
    data: mapCommunityPostPage(response.data.data),
  };
}

export async function getCommunityPost(postId: number) {
  const response = await apiClient.get<ApiResponse<CommunityPost>>(`/posts/${postId}`);
  return {
    ...response.data,
    data: mapCommunityPost(response.data.data),
  };
}

export async function createCommunityPost(data: CommunityPostPayload) {
  const response = await apiClient.post<ApiResponse<CommunityPost>>('/posts', serializeCommunityPostPayload(data));
  return {
    ...response.data,
    data: mapCommunityPost(response.data.data),
  };
}

export async function updateCommunityPost(postId: number, data: CommunityPostPayload) {
  const response = await apiClient.put<ApiResponse<CommunityPost>>(`/posts/${postId}`, serializeCommunityPostPayload(data));
  return {
    ...response.data,
    data: mapCommunityPost(response.data.data),
  };
}

export async function deleteCommunityPost(postId: number) {
  const response = await apiClient.delete<ApiResponse<void>>(`/posts/${postId}`);
  return response.data;
}

export async function uploadCommunityImage(file: File) {
  const formData = new FormData();
  formData.append('file', file);

  const response = await apiClient.post<ApiResponse<{ imageUrl: string }>>('/posts/images', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data;
}

export async function toggleCommunityLike(postId: number) {
  const response = await apiClient.post<ApiResponse<CommunityPost>>(`/posts/${postId}/like`);
  return {
    ...response.data,
    data: mapCommunityPost(response.data.data),
  };
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
