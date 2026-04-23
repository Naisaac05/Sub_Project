import apiClient from '../api';
import type { ApiResponse, UserResponse, UserRole, UserStatus } from '../types';

export interface AdminUserListItem {
  id: number;
  email: string;
  name: string;
  role: UserRole;
  status: UserStatus;
  jobTitle: string | null;
  createdAt: string;
}

export interface AdminUserDetail extends AdminUserListItem {
  provider: string | null;
  updatedAt: string;
  paymentCount: number;
  postCount: number;
  mentorProfileId: number | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface PasswordResetResult {
  temporaryPassword: string;
  mustChangePassword: boolean;
}

export interface AdminCreateResult {
  user: UserResponse;
  temporaryPassword: string;
}

export interface ListUsersParams {
  role?: UserRole;
  status?: UserStatus;
  q?: string;
  page?: number;
  size?: number;
}

// ─── /api/admin/users ───

export async function listUsers(params: ListUsersParams): Promise<PageResponse<AdminUserListItem>> {
  const res = await apiClient.get<ApiResponse<PageResponse<AdminUserListItem>>>('/admin/users', { params });
  return res.data.data!;
}

export async function getUserDetail(id: number): Promise<AdminUserDetail> {
  const res = await apiClient.get<ApiResponse<AdminUserDetail>>(`/admin/users/${id}`);
  return res.data.data!;
}

export async function deactivateUser(id: number, reason: string): Promise<void> {
  await apiClient.post(`/admin/users/${id}/deactivate`, { reason });
}

export async function reactivateUser(id: number): Promise<void> {
  await apiClient.post(`/admin/users/${id}/reactivate`);
}

export async function deleteUser(id: number, reason: string): Promise<void> {
  await apiClient.post(`/admin/users/${id}/delete`, { reason });
}

export async function resetUserPassword(id: number): Promise<PasswordResetResult> {
  const res = await apiClient.post<ApiResponse<PasswordResetResult>>(`/admin/users/${id}/reset-password`);
  return res.data.data!;
}

export async function swapMentor(menteeId: number, newMentorId: number, reason: string): Promise<void> {
  await apiClient.post(`/admin/users/${menteeId}/swap-mentor`, { newMentorId, reason });
}

// ─── /api/admin/admins (SUPER_ADMIN only) ───

export async function listAdmins(): Promise<UserResponse[]> {
  const res = await apiClient.get<ApiResponse<UserResponse[]>>('/admin/admins');
  return res.data.data!;
}

export interface AdminCreatePayload {
  email: string;
  name: string;
  jobTitle: string;
}

export async function createAdmin(payload: AdminCreatePayload): Promise<AdminCreateResult> {
  const res = await apiClient.post<ApiResponse<AdminCreateResult>>('/admin/admins', payload);
  return res.data.data!;
}

// ─── /api/auth/change-password ───

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await apiClient.post('/auth/change-password', { currentPassword, newPassword });
}
