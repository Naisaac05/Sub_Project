import apiClient from './api';
import { setTokens, clearTokens } from './token';
import type {
  ApiResponse,
  SignupRequest,
  LoginRequest,
  TokenResponse,
  UserResponse,
  UserUpdateRequest,
} from './types';

// Re-export token utilities for convenience
export { getAccessToken, getRefreshToken, clearTokens } from './token';

// ─── Auth API ───

/** 회원가입 */
export async function signup(data: SignupRequest): Promise<ApiResponse<UserResponse>> {
  const res = await apiClient.post<ApiResponse<UserResponse>>('/auth/signup', data);
  return res.data;
}

/** 로그인 — 성공 시 토큰을 localStorage에 저장 */
export async function login(data: LoginRequest): Promise<ApiResponse<TokenResponse>> {
  const res = await apiClient.post<ApiResponse<TokenResponse>>('/auth/login', data);
  if (res.data.success && res.data.data) {
    setTokens(res.data.data.accessToken, res.data.data.refreshToken);
  }
  return res.data;
}

/** 토큰 갱신 */
export async function refresh(): Promise<ApiResponse<TokenResponse>> {
  const { getRefreshToken } = await import('./token');
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }
  const res = await apiClient.post<ApiResponse<TokenResponse>>('/auth/refresh', { refreshToken });
  if (res.data.success && res.data.data) {
    setTokens(res.data.data.accessToken, res.data.data.refreshToken);
  }
  return res.data;
}

/** 로그아웃 */
export async function logout(): Promise<void> {
  try {
    await apiClient.post('/auth/logout');
  } catch {
    // 서버 에러가 나도 클라이언트 토큰은 삭제
  } finally {
    clearTokens();
  }
}

// ─── User API ───

/** 내 프로필 조회 */
export async function getMyProfile(): Promise<ApiResponse<UserResponse>> {
  const res = await apiClient.get<ApiResponse<UserResponse>>('/users/me');
  return res.data;
}

/** 내 프로필 수정 */
export async function updateMyProfile(data: UserUpdateRequest): Promise<ApiResponse<UserResponse>> {
  const res = await apiClient.put<ApiResponse<UserResponse>>('/users/me', data);
  return res.data;
}
