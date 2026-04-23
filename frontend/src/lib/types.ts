// ─── Backend API 공통 응답 ───
export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

// Spring Data Page<T> 응답 중 프론트에서 실제 쓰는 필드만.
export interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

// ─── Auth DTOs ───
export interface SignupRequest {
  email: string;
  password: string;
  name: string;
  role: 'MENTEE' | 'MENTOR';
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
}

// ─── User DTOs ───
export type UserRole = 'MENTEE' | 'MENTOR' | 'ADMIN' | 'SUPER_ADMIN';
export type UserStatus = 'ACTIVE' | 'DEACTIVATED' | 'DELETED';

export interface UserResponse {
  id: number;
  email: string;
  name: string;
  role: UserRole;
  status: UserStatus;
  jobTitle: string | null;
  mustChangePassword: boolean;
  createdAt: string;
}

export interface UserUpdateRequest {
  name?: string;
  password?: string;
}

// ─── Course DTOs ───
export interface CourseSummary {
  courseKey: string;
  title: string;
  iconString: string;
}

export interface MentoringCourseBox {
  icon?: string;
  title: string;
  color?: string;
  tags: string[];
  desc: string;
  isWide?: boolean;
}

export interface MentoringCourseDetail {
  id: number;
  courseKey: string;
  title: string;
  subtitle: string;
  iconString: string;
  descriptionTitle: string;
  descriptionText: string;
  boxes: MentoringCourseBox[];
  displayOrder: number;
  active: boolean;
}

// ─── Mentor DTOs ───
export interface MentorApplyRequest {
  courseKeys: string[];
  techStack?: string[];
  careerYears: number;
  company?: string;
  jobTitle?: string;
  portfolioUrl?: string;
  education?: string;
  certifications?: string[];
  preferredMenteeLevel?: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'ANY';
  bio?: string;
}

export interface MentorProfileResponse {
  id: number;
  userId: number;
  name: string;
  email: string;
  courses: CourseSummary[];
  techStack: string[];
  careerYears: number;
  company: string;
  jobTitle: string;
  portfolioUrl: string;
  education: string;
  certifications: string[];
  preferredMenteeLevel: 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'ANY' | null;
  bio: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectedReason: string | null;
  createdAt: string;
  updatedAt: string;
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
  courseKeys: string[];
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
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED' | 'TRIAL';
  rejectedReason: string | null;
  testScore: number | null;
  createdAt: string;
}

export interface MatchingAcceptRequest {
  accepted: boolean;
  rejectedReason?: string;
}
