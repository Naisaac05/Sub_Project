import apiClient from './api';
import { setAccessToken, clearAccessToken } from './token';
import type {
  ApiResponse,
  SignupRequest,
  LoginRequest,
  TokenResponse,
  UserResponse,
  UserUpdateRequest,
} from './types';

// Re-export token utilities for convenience
export { getAccessToken, clearAccessToken } from './token';

// Back-compat alias used by AuthContext
export const clearTokens = clearAccessToken;

// ─── Auth API ───

/** 회원가입 */
export async function signup(data: SignupRequest): Promise<ApiResponse<UserResponse>> {
  const res = await apiClient.post<ApiResponse<UserResponse>>('/auth/signup', data);
  return res.data;
}

/** 로그인 — 성공 시 access token을 localStorage에 저장 (refresh token은 HttpOnly 쿠키) */
export async function login(data: LoginRequest): Promise<ApiResponse<TokenResponse>> {
  const res = await apiClient.post<ApiResponse<TokenResponse>>('/auth/login', data);
  if (res.data.success && res.data.data) {
    setAccessToken(res.data.data.accessToken);
  }
  return res.data;
}

/** 토큰 갱신 — refresh token은 쿠키로 자동 전송 */
export async function refresh(): Promise<ApiResponse<TokenResponse>> {
  const res = await apiClient.post<ApiResponse<TokenResponse>>('/auth/refresh');
  if (res.data.success && res.data.data) {
    setAccessToken(res.data.data.accessToken);
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
    clearAccessToken();
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
