// ─── Enums ───
export type AssignmentType = 'TASK' | 'CODE_REVIEW';
export type AssignmentStatus = 'ASSIGNED' | 'SUBMITTED' | 'REVIEWED';
export type NoteType = 'SESSION_REVIEW' | 'WEEKLY_JOURNAL';

// ─── Dashboard ───
export interface DashboardResponse {
  progressRate: number;
  attendanceRate: number;
  mentoringEndDate: string | null;
  assignmentStats: { total: number; submitted: number; reviewed: number; };
  nextSession: { id: number; date: string; startTime: string; endTime: string; meetLink: string; category: string; } | null;
  recentActivities: { type: string; title: string; createdAt: string; }[];
  mentorInfo: { name: string; specialty: string[]; email: string; };
  communicationLinks: { discord: string | null; jitsiMeet: string | null; };
}

export interface EnrollmentResponse {
  matchingId: number; menteeName: string; mentorName: string; category: string;
  status: string; startDate: string; trialEndDate: string | null;
}

// ─── Curriculum ───
export interface CurriculumWeekResponse {
  id: number; weekNumber: number; title: string; description: string | null;
  topics: string[]; resources: string[]; isCompleted: boolean; completedAt: string | null;
}
export interface CurriculumResponse {
  id: number; matchingId: number; title: string; description: string | null;
  totalWeeks: number; startDate: string; endDate: string; discordUrl: string | null;
  weeks: CurriculumWeekResponse[];
}
export interface CurriculumWeekRequest {
  weekNumber: number; title: string; description?: string; topics: string[]; resources: string[];
}
export interface CurriculumCreateRequest {
  matchingId: number; title: string; description?: string; totalWeeks: number;
  startDate: string; endDate: string; discordUrl?: string; weeks: CurriculumWeekRequest[];
}
export interface CurriculumLimitResponse {
  maxWeeks: number;
  monthsBundled: number;
  paymentDate: string | null;
  hasConfirmedPayment: boolean;
}

// ─── Assignment ───
export interface SubmissionResponse {
  id: number; submissionUrl: string; submissionNote: string | null; submittedAt: string;
  feedbackContent: string | null; grade: string | null; feedbackAt: string | null;
}
export interface AssignmentResponse {
  id: number; matchingId: number; mentorId: number; type: AssignmentType;
  title: string; description: string | null; dueDate: string | null;
  referenceUrls: string[]; status: AssignmentStatus; submission: SubmissionResponse | null; createdAt: string;
}
export interface AssignmentCreateRequest {
  matchingId: number; type: AssignmentType; title: string; description?: string;
  dueDate?: string; referenceUrls?: string[];
}
export interface SubmissionRequest { submissionUrl: string; submissionNote?: string; }
export interface FeedbackRequest { feedbackContent: string; grade?: string; }

// ─── Learning Note ───
export interface NoteCommentResponse {
  id: number; authorId: number; authorName: string; content: string; createdAt: string;
}
export interface NoteResponse {
  id: number; matchingId: number; authorId: number; authorName: string;
  type: NoteType; sessionId: number | null; weekNumber: number | null;
  title: string; content: string; selfRating: number | null;
  comments: NoteCommentResponse[]; createdAt: string; updatedAt: string;
}
export interface NoteCreateRequest {
  matchingId: number; type: NoteType; sessionId?: number; weekNumber?: number;
  title: string; content: string; selfRating?: number;
}
export interface NoteCommentRequest { content: string; }

// ─── Career ───
export interface ResumeResponse {
  id: number; menteeId: number; matchingId: number; version: number;
  fileUrl: string; fileName: string; mentorFeedback: string | null;
  feedbackAt: string | null; uploadedAt: string;
}
export interface ResumeFeedbackRequest { mentorFeedback: string; }
export interface MockInterviewResponse {
  id: number; matchingId: number; interviewDate: string; topic: string;
  questionsAndAnswers: string | null; mentorFeedback: string | null;
  rating: number | null; createdAt: string;
}
export interface MockInterviewCreateRequest {
  matchingId: number; interviewDate: string; topic: string;
  questionsAndAnswers?: string; mentorFeedback?: string; rating?: number;
}

// ─── Certificate ───
export interface CertificateEligibilityResponse {
  eligible: boolean; progressRate: number; attendanceRate: number;
  assignmentSubmitRate: number; requiredProgress: number;
  requiredAttendance: number; requiredAssignmentRate: number;
}

// ─── Session (LMS context) ───
export type SessionStatus = 'PENDING' | 'SCHEDULED' | 'COMPLETED' | 'CANCELLED';
export interface SessionListResponse {
  id: number; matchingId: number; menteeId: number; mentorId: number;
  category: string; sessionDate: string; startTime: string; endTime: string;
  status: SessionStatus; title?: string | null; meetLink: string | null; memo: string | null;
  hasPendingChangeRequest: boolean; createdAt: string;
}

// ─── Time Slot ───
export interface TimeSlotResponse {
  id: number; matchingId: number; slotDate: string;
  startTime: string; endTime: string; isBooked: boolean;
  proposedByMentee: boolean;
}
export interface TimeSlotCreateRequest {
  slotDate: string; startTime: string; endTime: string;
}

// ─── Booking ───
export interface BookSessionRequest { slotId: number; memo?: string; }

// ─── Mentor direct session (free time) ───
export interface DirectSessionCreateRequest {
  sessionDate: string; startTime: string; endTime: string; memo?: string;
}

// ─── Change Request ───
export type ChangeRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export interface ChangeRequestResponse {
  id: number; sessionId: number; requesterId: number;
  newDate: string; newStartTime: string; newEndTime: string;
  reason: string | null; status: ChangeRequestStatus;
  createdAt: string; respondedAt: string | null;
}
export interface ChangeRequestCreateRequest {
  sessionId: number; newDate: string; newStartTime: string;
  newEndTime: string; reason?: string;
}
