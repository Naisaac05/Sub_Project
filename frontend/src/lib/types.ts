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

// ─── Test DTOs ───
export interface TestListResponse {
  id: number;
  title: string;
  category: string;
  difficulty: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  timeLimit: number;
  questionCount: number;
  passingScore: number;
}

export interface QuestionResponse {
  id: number;
  content: string;
  options: string[];
  score: number;
  orderIndex: number;
}

export interface TestDetailResponse {
  id: number;
  title: string;
  description: string;
  category: string;
  difficulty: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED';
  timeLimit: number;
  passingScore: number;
  questions: QuestionResponse[];
}

export interface AnswerRequest {
  questionId: number;
  selectedAnswer: number;
}

export interface TestSubmitRequest {
  answers: AnswerRequest[];
}

export interface TestResultResponse {
  id: number;
  testId: number;
  testTitle: string;
  category: string;
  totalScore: number;
  correctCount: number;
  questionCount: number;
  passed: boolean;
  submittedAt: string;
}

// ─── Matching DTOs ───
export interface MentorRecommendResponse {
  mentorId: number;
  name: string;
  specialty: string[];
  careerYears: number;
  company: string;
  bio: string;
  matchScore: number;
}

export interface MatchingRequest {
  mentorId: number;
  category: string;
  testResultId?: number;
  message?: string;
}

export interface MatchingResponse {
  id: number;
  menteeId: number;
  menteeName: string;
  mentorId: number;
  mentorName: string;
  category: string;
  message: string;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED';
  rejectedReason: string | null;
  testScore: number | null;
  createdAt: string;
}

export interface MatchingAcceptRequest {
  accepted: boolean;
  rejectedReason?: string;
}
