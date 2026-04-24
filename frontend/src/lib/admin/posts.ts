import apiClient from '../api';
import type { ApiResponse } from '../types';

export interface AdminPostListItem {
  id: number;
  title: string;
  category: string;
  authorId: number | null;
  authorName: string;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  createdAt: string;
  deleted: boolean;
  deletedAt: string | null;
}

export interface AdminPostCommentItem {
  id: number;
  authorId: number | null;
  authorName: string;
  content: string;
  createdAt: string;
  deleted: boolean;
  deletionReason: string | null;
  deletedBy: number | null;
  deletedAt: string | null;
}

export interface AdminPostDetail {
  id: number;
  title: string;
  content: string;
  category: string;
  authorId: number | null;
  authorName: string;
  authorEmail: string | null;
  authorRole: string | null;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  createdAt: string;
  updatedAt: string;
  deleted: boolean;
  deletionReason: string | null;
  deletedBy: number | null;
  deletedAt: string | null;
  comments: AdminPostCommentItem[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ListAdminPostsParams {
  page?: number;
  size?: number;
  sort?: string;
  category?: string;
  q?: string;
  from?: string; // YYYY-MM-DD
  to?: string;
  includeDeleted?: boolean;
}

export async function listAdminPosts(
  params: ListAdminPostsParams
): Promise<PageResponse<AdminPostListItem>> {
  const res = await apiClient.get<ApiResponse<PageResponse<AdminPostListItem>>>(
    '/admin/posts', { params }
  );
  return res.data.data!;
}

export async function listAdminPostCategories(): Promise<string[]> {
  const res = await apiClient.get<ApiResponse<string[]>>('/admin/posts/categories');
  return res.data.data!;
}

export async function getAdminPost(id: number): Promise<AdminPostDetail> {
  const res = await apiClient.get<ApiResponse<AdminPostDetail>>(`/admin/posts/${id}`);
  return res.data.data!;
}

export async function deleteAdminPost(
  id: number, reason: string
): Promise<AdminPostDetail> {
  const res = await apiClient.delete<ApiResponse<AdminPostDetail>>(
    `/admin/posts/${id}`, { data: { reason } }
  );
  return res.data.data!;
}

export async function deleteAdminComment(
  postId: number, commentId: number, reason: string
): Promise<AdminPostCommentItem> {
  const res = await apiClient.delete<ApiResponse<AdminPostCommentItem>>(
    `/admin/posts/${postId}/comments/${commentId}`, { data: { reason } }
  );
  return res.data.data!;
}
