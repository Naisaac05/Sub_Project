// ─── Backend API 공통 응답 ───
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

// ─── Auth DTOs ───
export interface SignupRequest {
  email: string;
  password: string;
  name: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

export interface TokenRefreshRequest {
  refreshToken: string;
}

// ─── User DTOs ───
export interface UserResponse {
  id: number;
  email: string;
  name: string;
  role: 'MENTEE' | 'MENTOR' | 'ADMIN';
  createdAt: string;
}

export interface UserUpdateRequest {
  name?: string;
  password?: string;
}

// ─── Mentor DTOs ───
export interface MentorApplyRequest {
  specialty: string[];
  careerYears: number;
  company?: string;
  bio?: string;
}

export interface MentorProfileResponse {
  id: number;
  userId: number;
  name: string;
  email: string;
  specialty: string[];
  careerYears: number;
  company: string;
  bio: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
}
