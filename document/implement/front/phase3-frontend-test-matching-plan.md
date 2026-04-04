# Phase 3: 테스트 & 멘토 매칭 — Frontend 구현 계획서

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 백엔드 Phase 3 API(테스트 4개 + 매칭 5개 엔드포인트)를 프론트엔드와 연결하여, 실력 테스트 응시/채점과 멘토 추천/매칭 신청 기능을 완성한다.

**Architecture:** 기존 `lib/api.ts` axios 클라이언트 + `lib/types.ts` 인터페이스 패턴을 그대로 확장한다. 테스트 API 서비스(`lib/test.ts`)와 매칭 API 서비스(`lib/matching.ts`)를 신규 생성하고, 각 페이지에서 호출한다. 현재 mock 데이터로 되어 있는 `/tests`, `/mentors` 페이지를 실제 API 연동으로 교체하고, 테스트 응시(`/tests/[id]`), 결과 확인(`/tests/results`), 매칭 내역(`/matching`) 페이지를 신규 생성한다.

**Tech Stack:** Next.js 14 App Router, TypeScript, Tailwind CSS, Axios, Lucide React Icons

---

## 1. 현재 상태 분석

### 이미 완료된 항목 (Phase 2)

| 항목 | 상태 |
|------|------|
| JWT 인증 흐름 (login/signup/refresh/logout) | ✅ |
| AuthContext 전역 상태 | ✅ |
| API 클라이언트 (axios + 401 자동 갱신) | ✅ |
| types.ts (ApiResponse, User, Auth DTOs) | ✅ |
| Header (로그인 상태 분기 + 드롭다운) | ✅ |
| MyPage (프로필 조회/수정) | ✅ |
| Tests 페이지 (mock 데이터 UI) | ✅ UI만 |
| Mentors 페이지 (mock 데이터 UI) | ✅ UI만 |

### Phase 3에서 활용할 기존 코드

| 기존 코드 | 활용 방식 |
|-----------|-----------|
| `lib/api.ts` (apiClient) | 모든 API 호출에 재사용 (JWT 자동 첨부) |
| `lib/types.ts` | 신규 DTO 인터페이스 추가 |
| `contexts/AuthContext.tsx` | 로그인 상태 확인, userId 접근 |
| `components/layout/Header.tsx` | 네비게이션 링크 추가 |
| `app/tests/page.tsx` | mock 데이터 → API 연동으로 교체 |
| `app/mentors/page.tsx` | mock 데이터 → 추천 API 연동으로 교체 |

---

## 2. 백엔드 API 계약 (연결 대상)

### Test API (`/api/tests`)

| HTTP | 경로 | 응답 타입 | 설명 |
|------|------|-----------|------|
| GET | `/api/tests?category=` | `ApiResponse<TestListResponse[]>` | 테스트 목록 |
| GET | `/api/tests/{id}` | `ApiResponse<TestDetailResponse>` | 테스트 상세 + 문제 |
| POST | `/api/tests/{id}/submit` | `ApiResponse<TestResultResponse>` | 답안 제출 + 채점 |
| GET | `/api/tests/results` | `ApiResponse<TestResultResponse[]>` | 내 결과 목록 |

### Matching API (`/api/matching`)

| HTTP | 경로 | 응답 타입 | 설명 |
|------|------|-----------|------|
| GET | `/api/matching/recommend?category=` | `ApiResponse<MentorRecommendResponse[]>` | 멘토 추천 |
| POST | `/api/matching/request` | `ApiResponse<MatchingResponse>` | 매칭 신청 |
| PUT | `/api/matching/{id}/accept` | `ApiResponse<MatchingResponse>` | 수락/거절 |
| GET | `/api/matching/mentee` | `ApiResponse<MatchingResponse[]>` | 멘티 매칭 내역 |
| GET | `/api/matching/mentor` | `ApiResponse<MatchingResponse[]>` | 멘토 매칭 요청 |

---

## 3. 파일 구조

### 신규 생성 파일 (8개)

| 파일 | 설명 |
|------|------|
| `src/lib/test.ts` | 테스트 API 서비스 (4개 함수) |
| `src/lib/matching.ts` | 매칭 API 서비스 (5개 함수) |
| `src/app/tests/[id]/page.tsx` | 테스트 응시 페이지 (문제 풀기 + 제출) |
| `src/app/tests/results/page.tsx` | 내 테스트 결과 목록 페이지 |
| `src/app/tests/result/[id]/page.tsx` | 테스트 결과 상세 (제출 직후 이동) |
| `src/app/matching/page.tsx` | 매칭 내역 페이지 (멘티/멘토 통합) |
| `src/app/mentors/[id]/page.tsx` | 멘토 상세 + 매칭 신청 페이지 |
| `src/app/matching/recommend/page.tsx` | 멘토 추천 목록 페이지 (테스트 결과 기반) |

### 수정 파일 (5개)

| 파일 | 변경 사항 |
|------|-----------|
| `src/lib/types.ts` | Test/Matching 관련 TypeScript 인터페이스 12개 추가 |
| `src/app/tests/page.tsx` | mock 데이터 제거, API 연동, 동적 카테고리 |
| `src/app/mentors/page.tsx` | mock 데이터 제거, 추천 API 연동, 매칭 신청 버튼 |
| `src/app/mypage/page.tsx` | 테스트 결과 요약 + 매칭 내역 요약 섹션 추가 |
| `src/components/layout/Header.tsx` | 드롭다운에 "테스트 결과", "매칭 내역" 링크 추가 |

---

## 4. 구현 태스크

### Task 1: TypeScript 인터페이스 추가 (`types.ts`)

**Files:**
- Modify: `src/lib/types.ts:44` (기존 Mentor DTOs 아래에 추가)

- [ ] **Step 1: `src/lib/types.ts` 끝에 Test/Matching 인터페이스 추가**

```typescript
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
```

- [ ] **Step 2: 빌드 확인**

```bash
cd frontend && npm run build
```

---

### Task 2: 테스트 API 서비스 (`lib/test.ts`)

**Files:**
- Create: `src/lib/test.ts`

- [ ] **Step 1: `src/lib/test.ts` 생성**

```typescript
import apiClient from './api';
import type {
  ApiResponse,
  TestListResponse,
  TestDetailResponse,
  TestSubmitRequest,
  TestResultResponse,
} from './types';

/** 테스트 목록 조회 (category null이면 전체) */
export async function getTests(category?: string): Promise<ApiResponse<TestListResponse[]>> {
  const params = category ? { category } : {};
  const res = await apiClient.get<ApiResponse<TestListResponse[]>>('/tests', { params });
  return res.data;
}

/** 테스트 상세 + 문제 목록 (정답 미포함) */
export async function getTestDetail(testId: number): Promise<ApiResponse<TestDetailResponse>> {
  const res = await apiClient.get<ApiResponse<TestDetailResponse>>(`/tests/${testId}`);
  return res.data;
}

/** 답안 제출 + 자동 채점 */
export async function submitTest(testId: number, data: TestSubmitRequest): Promise<ApiResponse<TestResultResponse>> {
  const res = await apiClient.post<ApiResponse<TestResultResponse>>(`/tests/${testId}/submit`, data);
  return res.data;
}

/** 내 테스트 결과 목록 */
export async function getMyResults(): Promise<ApiResponse<TestResultResponse[]>> {
  const res = await apiClient.get<ApiResponse<TestResultResponse[]>>('/tests/results');
  return res.data;
}
```

- [ ] **Step 2: 빌드 확인**

---

### Task 3: 매칭 API 서비스 (`lib/matching.ts`)

**Files:**
- Create: `src/lib/matching.ts`

- [ ] **Step 1: `src/lib/matching.ts` 생성**

```typescript
import apiClient from './api';
import type {
  ApiResponse,
  MentorRecommendResponse,
  MatchingRequest,
  MatchingResponse,
  MatchingAcceptRequest,
} from './types';

/** 테스트 결과 기반 멘토 추천 */
export async function recommendMentors(category: string): Promise<ApiResponse<MentorRecommendResponse[]>> {
  const res = await apiClient.get<ApiResponse<MentorRecommendResponse[]>>('/matching/recommend', {
    params: { category },
  });
  return res.data;
}

/** 매칭 신청 */
export async function requestMatching(data: MatchingRequest): Promise<ApiResponse<MatchingResponse>> {
  const res = await apiClient.post<ApiResponse<MatchingResponse>>('/matching/request', data);
  return res.data;
}

/** 매칭 수락/거절 (멘토 전용) */
export async function acceptMatching(matchingId: number, data: MatchingAcceptRequest): Promise<ApiResponse<MatchingResponse>> {
  const res = await apiClient.put<ApiResponse<MatchingResponse>>(`/matching/${matchingId}/accept`, data);
  return res.data;
}

/** 멘티 입장 매칭 내역 */
export async function getMyMatchingsAsMentee(): Promise<ApiResponse<MatchingResponse[]>> {
  const res = await apiClient.get<ApiResponse<MatchingResponse[]>>('/matching/mentee');
  return res.data;
}

/** 멘토 입장 매칭 요청 목록 */
export async function getMyMatchingsAsMentor(): Promise<ApiResponse<MatchingResponse[]>> {
  const res = await apiClient.get<ApiResponse<MatchingResponse[]>>('/matching/mentor');
  return res.data;
}
```

- [ ] **Step 2: 빌드 확인**

---

### Task 4: 테스트 목록 페이지 API 연동 (`/tests`)

**Files:**
- Modify: `src/app/tests/page.tsx` (전체 교체)

- [ ] **Step 1: `tests/page.tsx` 리팩토링 — mock 데이터 제거, API 연동**

핵심 변경 사항:
- `useState`로 관리하던 mock `tests` 배열을 `useEffect` + `getTests(category)` API 호출로 교체
- 로딩/에러 상태 추가
- 카테고리 필터 클릭 시 API 재호출 (category 파라미터 전달)
- 각 테스트 카드 클릭 시 `/tests/{id}` 페이지로 이동 (`Link` 또는 `router.push`)
- "테스트 결과 보기" 링크를 `/tests/results`로 연결
- 난이도 표시를 백엔드 Enum (`BEGINNER`/`INTERMEDIATE`/`ADVANCED`) 기준으로 매핑

```typescript
// 핵심 변경 포인트:

// 1. imports 추가
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { getTests } from '@/lib/test';
import type { TestListResponse } from '@/lib/types';

// 2. state 변경
const [tests, setTests] = useState<TestListResponse[]>([]);
const [isLoading, setIsLoading] = useState(true);
const [error, setError] = useState('');

// 3. API 호출
useEffect(() => {
  const fetchTests = async () => {
    setIsLoading(true);
    try {
      const category = selectedCategory === '전체' ? undefined : selectedCategory;
      const res = await getTests(category);
      if (res.success) setTests(res.data);
    } catch {
      setError('테스트 목록을 불러오지 못했습니다.');
    } finally {
      setIsLoading(false);
    }
  };
  fetchTests();
}, [selectedCategory]);

// 4. 난이도 매핑
const difficultyLabel: Record<string, string> = {
  BEGINNER: '입문',
  INTERMEDIATE: '중급',
  ADVANCED: '고급',
};
const difficultyColor: Record<string, string> = {
  BEGINNER: 'bg-green-50 text-green-600 border-green-100',
  INTERMEDIATE: 'bg-amber-50 text-amber-600 border-amber-100',
  ADVANCED: 'bg-red-50 text-red-600 border-red-100',
};

// 5. 카드 클릭 → 테스트 응시 페이지
<Link href={`/tests/${test.id}`}>
```

- [ ] **Step 2: 빌드 확인 + 브라우저에서 테스트 목록 로드 확인**

---

### Task 5: 테스트 응시 페이지 (`/tests/[id]`)

**Files:**
- Create: `src/app/tests/[id]/page.tsx`

- [ ] **Step 1: 테스트 응시 페이지 생성**

페이지 구조:
1. `useEffect`로 `getTestDetail(id)` 호출 → 테스트 정보 + 문제 목록 로드
2. 문제별 선택 답안을 `Record<number, number>` (questionId → selectedAnswer)로 관리
3. 제한 시간 카운트다운 타이머 표시 (timeLimit 분 → 초 단위)
4. "제출하기" 버튼 클릭 시 `submitTest(id, { answers })` 호출
5. 채점 결과 표시 (점수, 정답 수, 합격 여부)
6. 결과 확인 후 → "결과 목록 보기" 또는 "멘토 추천 받기" 링크

```typescript
'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getTestDetail, submitTest } from '@/lib/test';
import type { TestDetailResponse, TestResultResponse } from '@/lib/types';
import { Clock, CheckCircle, XCircle, ArrowRight, ArrowLeft, Loader2, AlertCircle } from 'lucide-react';

export default function TestTakePage() {
  const params = useParams();
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();
  const testId = Number(params.id);

  const [test, setTest] = useState<TestDetailResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  // 답안 관리: questionId → selectedAnswer
  const [answers, setAnswers] = useState<Record<number, number>>({});
  // 현재 문제 인덱스
  const [currentIndex, setCurrentIndex] = useState(0);
  // 제출 상태
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<TestResultResponse | null>(null);
  // 타이머 (초)
  const [timeLeft, setTimeLeft] = useState(0);

  // 비로그인 리다이렉트
  useEffect(() => {
    if (!authLoading && !isLoggedIn) router.replace('/auth/login');
  }, [authLoading, isLoggedIn, router]);

  // 테스트 데이터 로드
  useEffect(() => {
    const fetchTest = async () => {
      try {
        const res = await getTestDetail(testId);
        if (res.success) {
          setTest(res.data);
          setTimeLeft(res.data.timeLimit * 60);
        }
      } catch {
        setError('테스트를 불러오지 못했습니다.');
      } finally {
        setIsLoading(false);
      }
    };
    if (testId) fetchTest();
  }, [testId]);

  // 카운트다운 타이머
  useEffect(() => {
    if (!test || result || timeLeft <= 0) return;
    const timer = setInterval(() => {
      setTimeLeft((prev) => {
        if (prev <= 1) { clearInterval(timer); return 0; }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [test, result, timeLeft]);

  // 시간 초과 시 자동 제출
  useEffect(() => {
    if (timeLeft === 0 && test && !result && !isSubmitting) {
      handleSubmit();
    }
  }, [timeLeft]);

  const handleSelectAnswer = (questionId: number, answer: number) => {
    setAnswers((prev) => ({ ...prev, [questionId]: answer }));
  };

  const handleSubmit = async () => {
    if (!test) return;
    setIsSubmitting(true);
    try {
      const answerList = test.questions.map((q) => ({
        questionId: q.id,
        selectedAnswer: answers[q.id] ?? 0,
      }));
      const res = await submitTest(testId, { answers: answerList });
      if (res.success) setResult(res.data);
    } catch {
      setError('답안 제출에 실패했습니다.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const formatTime = (seconds: number) => {
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  // ... 렌더링: 로딩/에러/문제풀기/결과 화면
}
```

UI 구성:
- **상단 바:** 테스트 제목 + 남은 시간 타이머 + 진행률 (N/M 문항)
- **문제 영역:** 문제 내용 + 4지선다 옵션 (라디오 버튼 스타일)
- **하단 네비게이션:** 이전/다음 버튼 + 문제 번호 인디케이터 (클릭으로 이동)
- **제출 버튼:** 모든 문항 답변 시 활성화, 미답변 있으면 경고
- **결과 화면:** 점수, 정답 수/총 문항 수, 합격/불합격 뱃지, "멘토 추천 받기" CTA

- [ ] **Step 2: 빌드 확인 + 브라우저에서 테스트 응시 흐름 확인**

---

### Task 6: 테스트 결과 목록 페이지 (`/tests/results`)

**Files:**
- Create: `src/app/tests/results/page.tsx`

- [ ] **Step 1: 테스트 결과 목록 페이지 생성**

```typescript
'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getMyResults } from '@/lib/test';
import type { TestResultResponse } from '@/lib/types';
import { Trophy, CheckCircle, XCircle, Clock, ArrowRight, Loader2, FileText } from 'lucide-react';

export default function TestResultsPage() {
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();

  const [results, setResults] = useState<TestResultResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!authLoading && !isLoggedIn) router.replace('/auth/login');
  }, [authLoading, isLoggedIn, router]);

  useEffect(() => {
    const fetchResults = async () => {
      try {
        const res = await getMyResults();
        if (res.success) setResults(res.data);
      } catch {
        // 에러 처리
      } finally {
        setIsLoading(false);
      }
    };
    if (isLoggedIn) fetchResults();
  }, [isLoggedIn]);

  // 렌더링: 결과 카드 목록
  // 각 카드: testTitle, category, totalScore/100, correctCount/questionCount, passed 뱃지, submittedAt
  // 합격한 결과 → "멘토 추천 받기" 버튼 → /matching/recommend?category={category}&resultId={id}
}
```

UI 구성:
- **헤더 섹션:** "내 테스트 결과" 타이틀 + 총 응시 횟수
- **결과 카드 목록:** 최신순 정렬, 각 카드에:
  - 테스트 제목, 분야 뱃지, 점수 (원형 게이지 또는 바), 정답 수, 합격/불합격 뱃지
  - 응시일 표시
  - 합격 시 "멘토 추천 받기" CTA 버튼
- **빈 상태:** "아직 응시한 테스트가 없습니다" + "테스트 보러가기" 링크

- [ ] **Step 2: 빌드 확인**

---

### Task 7: 멘토 추천 페이지 (`/matching/recommend`)

**Files:**
- Create: `src/app/matching/recommend/page.tsx`

- [ ] **Step 1: 멘토 추천 페이지 생성**

```typescript
'use client';

import { useState, useEffect } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { recommendMentors, requestMatching } from '@/lib/matching';
import type { MentorRecommendResponse } from '@/lib/types';
import { Star, Award, Briefcase, Send, Loader2, CheckCircle } from 'lucide-react';

export default function MentorRecommendPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const category = searchParams.get('category') || '';
  const testResultId = searchParams.get('resultId');
  const { isLoggedIn, isLoading: authLoading } = useAuth();

  const [mentors, setMentors] = useState<MentorRecommendResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // 매칭 신청 모달 상태
  const [selectedMentor, setSelectedMentor] = useState<MentorRecommendResponse | null>(null);
  const [matchMessage, setMatchMessage] = useState('');
  const [isRequesting, setIsRequesting] = useState(false);
  const [requestSuccess, setRequestSuccess] = useState(false);

  useEffect(() => {
    if (!authLoading && !isLoggedIn) router.replace('/auth/login');
  }, [authLoading, isLoggedIn, router]);

  useEffect(() => {
    const fetchMentors = async () => {
      if (!category) return;
      try {
        const res = await recommendMentors(category);
        if (res.success) setMentors(res.data);
      } catch {
        // 에러 처리
      } finally {
        setIsLoading(false);
      }
    };
    if (isLoggedIn && category) fetchMentors();
  }, [isLoggedIn, category]);

  const handleRequestMatching = async () => {
    if (!selectedMentor) return;
    setIsRequesting(true);
    try {
      const res = await requestMatching({
        mentorId: selectedMentor.mentorId,
        category,
        testResultId: testResultId ? Number(testResultId) : undefined,
        message: matchMessage || undefined,
      });
      if (res.success) setRequestSuccess(true);
    } catch (err: unknown) {
      const axiosError = err as { response?: { data?: { message?: string } } };
      alert(axiosError.response?.data?.message || '매칭 신청에 실패했습니다.');
    } finally {
      setIsRequesting(false);
    }
  };

  // 렌더링: 추천 멘토 카드 + 매칭 신청 모달
}
```

UI 구성:
- **헤더:** "{category} 분야 추천 멘토" + matchScore 기반 정렬 설명
- **멘토 카드 목록:** 각 카드에:
  - 이름, 회사, 경력, 전문 분야 태그, 자기소개
  - matchScore 게이지 (0~100) — 적합도 시각화
  - "매칭 신청" 버튼
- **매칭 신청 모달:**
  - 선택한 멘토 정보 요약
  - 신청 메시지 textarea (500자 제한)
  - 신청하기 / 취소 버튼
- **신청 성공 화면:** 성공 메시지 + "매칭 내역 보기" 링크

- [ ] **Step 2: 빌드 확인**

---

### Task 8: 매칭 내역 페이지 (`/matching`)

**Files:**
- Create: `src/app/matching/page.tsx`

- [ ] **Step 1: 매칭 내역 페이지 생성**

```typescript
'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import { getMyMatchingsAsMentee, getMyMatchingsAsMentor, acceptMatching } from '@/lib/matching';
import type { MatchingResponse } from '@/lib/types';
import { Clock, CheckCircle, XCircle, AlertCircle, Loader2, MessageSquare } from 'lucide-react';

export default function MatchingPage() {
  const router = useRouter();
  const { user, isLoggedIn, isLoading: authLoading } = useAuth();

  const [menteeMatchings, setMenteeMatchings] = useState<MatchingResponse[]>([]);
  const [mentorMatchings, setMentorMatchings] = useState<MatchingResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [activeTab, setActiveTab] = useState<'mentee' | 'mentor'>('mentee');

  useEffect(() => {
    if (!authLoading && !isLoggedIn) router.replace('/auth/login');
  }, [authLoading, isLoggedIn, router]);

  useEffect(() => {
    const fetchMatchings = async () => {
      try {
        const menteeRes = await getMyMatchingsAsMentee();
        if (menteeRes.success) setMenteeMatchings(menteeRes.data);

        if (user?.role === 'MENTOR') {
          const mentorRes = await getMyMatchingsAsMentor();
          if (mentorRes.success) setMentorMatchings(mentorRes.data);
        }
      } catch {
        // 에러 처리
      } finally {
        setIsLoading(false);
      }
    };
    if (isLoggedIn) fetchMatchings();
  }, [isLoggedIn, user]);

  // 멘토의 수락/거절 처리
  const handleAccept = async (matchingId: number, accepted: boolean, reason?: string) => {
    try {
      const res = await acceptMatching(matchingId, { accepted, rejectedReason: reason });
      if (res.success) {
        // 목록 갱신
        setMentorMatchings((prev) =>
          prev.map((m) => (m.id === matchingId ? res.data : m))
        );
      }
    } catch {
      alert('처리에 실패했습니다.');
    }
  };

  // 렌더링: 탭 (내 신청 / 받은 요청) + 매칭 카드 목록
}
```

UI 구성:
- **탭 네비게이션:**
  - "내 신청 내역" (멘티 역할 — 모든 사용자)
  - "받은 매칭 요청" (멘토 역할 — MENTOR만 표시)
- **멘티 탭 — 매칭 카드:**
  - 멘토 이름, 분야, 신청 메시지, 테스트 점수 (있으면)
  - 상태 뱃지: PENDING(노란색), ACCEPTED(초록색), REJECTED(빨간색), CANCELLED(회색)
  - 거절 시 사유 표시
  - 신청일
- **멘토 탭 — 매칭 요청 카드:**
  - 멘티 이름, 분야, 신청 메시지, 테스트 점수
  - PENDING 상태 → "수락" / "거절" 버튼
  - 거절 시 사유 입력 모달
  - ACCEPTED/REJECTED 상태면 처리 완료 표시
- **빈 상태:** 각 탭별 "매칭 내역이 없습니다" 안내

- [ ] **Step 2: 빌드 확인**

---

### Task 9: 멘토 찾기 페이지 API 연동 (`/mentors`)

**Files:**
- Modify: `src/app/mentors/page.tsx` (전체 교체)

- [ ] **Step 1: `mentors/page.tsx` 리팩토링**

핵심 변경 사항:
- mock `mentorData` 배열 제거
- `recommendMentors(category)` API 호출로 교체
- 카테고리 선택 시 해당 분야 추천 멘토 로드
- 각 멘토 카드에 matchScore 표시 추가
- "매칭 신청" 버튼 → 매칭 신청 모달 또는 `/matching/recommend?category=` 이동
- 로딩/에러/빈 상태 처리

```typescript
// 핵심 변경 포인트:

// 1. imports
import { recommendMentors } from '@/lib/matching';
import type { MentorRecommendResponse } from '@/lib/types';
import { useAuth } from '@/contexts/AuthContext';

// 2. state 변경
const [mentors, setMentors] = useState<MentorRecommendResponse[]>([]);
const [isLoading, setIsLoading] = useState(false);

// 3. 카테고리 선택 시 API 호출
useEffect(() => {
  if (selectedSpecialty === '전체') {
    setMentors([]);
    return;
  }
  const fetchMentors = async () => {
    setIsLoading(true);
    try {
      const res = await recommendMentors(selectedSpecialty);
      if (res.success) setMentors(res.data);
    } catch {
      // 에러 처리
    } finally {
      setIsLoading(false);
    }
  };
  fetchMentors();
}, [selectedSpecialty]);

// 4. 멘토 카드에 matchScore 게이지 추가
// 5. "매칭 신청" 버튼 추가
```

> **참고:** `전체` 선택 시에는 모든 카테고리의 멘토를 한번에 로드할 수 없으므로 (API가 category 필수), 분야를 먼저 선택하라는 안내 메시지를 표시하거나, 기존 mock 데이터를 기본 표시로 유지할 수 있음. 구현 시점에 판단.

- [ ] **Step 2: 빌드 확인 + 브라우저에서 멘토 추천 로드 확인**

---

### Task 10: Header 네비게이션 링크 추가

**Files:**
- Modify: `src/components/layout/Header.tsx:119-137` (드롭다운 메뉴 영역)

- [ ] **Step 1: 드롭다운 메뉴에 "테스트 결과", "매칭 내역" 링크 추가**

```typescript
// Header.tsx 드롭다운 메뉴 항목 (기존 "마이페이지"와 "로그아웃" 사이에 추가)

import { User, LogOut, ChevronDown, FileText, Users } from 'lucide-react';

// 드롭다운 메뉴 영역:
<div className="py-1">
  <Link href="/mypage" onClick={() => setDropdownOpen(false)}
    className="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400
             hover:text-white hover:bg-white/5 transition-colors">
    <User size={16} /> 마이페이지
  </Link>
  <Link href="/tests/results" onClick={() => setDropdownOpen(false)}
    className="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400
             hover:text-white hover:bg-white/5 transition-colors">
    <FileText size={16} /> 테스트 결과
  </Link>
  <Link href="/matching" onClick={() => setDropdownOpen(false)}
    className="flex items-center gap-3 px-4 py-2.5 text-sm text-gray-400
             hover:text-white hover:bg-white/5 transition-colors">
    <Users size={16} /> 매칭 내역
  </Link>
  <button onClick={handleLogout} className="...">
    <LogOut size={16} /> 로그아웃
  </button>
</div>
```

모바일 메뉴에도 동일하게 추가.

- [ ] **Step 2: 빌드 확인**

---

### Task 11: 마이페이지 확장 (`/mypage`)

**Files:**
- Modify: `src/app/mypage/page.tsx` (프로필 카드 아래에 섹션 추가)

- [ ] **Step 1: 마이페이지에 테스트 결과 요약 + 매칭 내역 요약 추가**

```typescript
// 추가 imports
import { getMyResults } from '@/lib/test';
import { getMyMatchingsAsMentee, getMyMatchingsAsMentor } from '@/lib/matching';
import type { TestResultResponse, MatchingResponse } from '@/lib/types';
import { FileText, Users, Trophy } from 'lucide-react';

// 추가 state
const [recentResults, setRecentResults] = useState<TestResultResponse[]>([]);
const [recentMatchings, setRecentMatchings] = useState<MatchingResponse[]>([]);

// useEffect에서 데이터 로드
useEffect(() => {
  if (!isLoggedIn) return;
  const fetchData = async () => {
    try {
      const [resultsRes, matchingsRes] = await Promise.all([
        getMyResults(),
        getMyMatchingsAsMentee(),
      ]);
      if (resultsRes.success) setRecentResults(resultsRes.data.slice(0, 3));
      if (matchingsRes.success) setRecentMatchings(matchingsRes.data.slice(0, 3));
    } catch {
      // 무시
    }
  };
  fetchData();
}, [isLoggedIn]);
```

프로필 카드 아래에 추가할 섹션:

**최근 테스트 결과 (최대 3개):**
- 테스트 제목, 점수, 합격 여부, 응시일
- "전체 보기" → `/tests/results`

**최근 매칭 내역 (최대 3개):**
- 멘토 이름, 분야, 상태 뱃지, 신청일
- "전체 보기" → `/matching`

- [ ] **Step 2: 빌드 확인**

---

## 5. 구현 순서 (권장)

```
Task 1: types.ts 인터페이스 추가           ← 모든 태스크의 기반
  ↓
Task 2: lib/test.ts API 서비스             ← 테스트 관련 태스크 기반
Task 3: lib/matching.ts API 서비스         ← 매칭 관련 태스크 기반
  ↓
Task 4: /tests 페이지 API 연동             ← mock → real
Task 5: /tests/[id] 응시 페이지            ← 핵심 기능
Task 6: /tests/results 결과 페이지
  ↓
Task 7: /matching/recommend 멘토 추천      ← 테스트 결과 → 멘토 추천 흐름
Task 8: /matching 매칭 내역                ← 매칭 관리
Task 9: /mentors 페이지 API 연동           ← mock → real
  ↓
Task 10: Header 링크 추가                  ← 네비게이션 연결
Task 11: MyPage 확장                       ← 대시보드 역할
```

---

## 6. 검증 시나리오

### 시나리오 A: 테스트 응시 전체 흐름

```
1. 멘티 로그인
2. /tests → 테스트 목록 확인 (API 데이터)
3. 테스트 카드 클릭 → /tests/{id} → 문제 로드
4. 문제 풀기 (4지선다 선택) → 제출
5. 채점 결과 확인 (점수, 합격 여부)
6. /tests/results → 결과 목록에 방금 결과 표시
```

### 시나리오 B: 멘토 매칭 전체 흐름

```
1. 멘티가 테스트 합격 후 → "멘토 추천 받기" 클릭
2. /matching/recommend?category=Java → 추천 멘토 목록
3. 멘토 선택 → 매칭 신청 (메시지 입력)
4. /matching → 신청 내역 PENDING 확인
5. 멘토 로그인 → /matching → "받은 요청" 탭 → 수락/거절
6. 멘티 → /matching → 상태 변경 확인 (ACCEPTED/REJECTED)
```

### 시나리오 C: 마이페이지 확인

```
1. 로그인 후 /mypage → 프로필 + 최근 테스트 결과 + 매칭 내역 표시
2. "전체 보기" 클릭 → 각 상세 페이지로 이동
```

---

## 7. 참고 사항

### 카테고리 매핑

백엔드 Test.category 값(예: "Java", "Spring", "React")과 프론트엔드 필터 UI의 카테고리 이름이 정확히 일치해야 합니다. 실제 데이터가 로드된 후 카테고리 필터를 동적으로 생성하는 것을 권장합니다.

### 난이도 Enum 매핑

| 백엔드 (Difficulty) | 프론트엔드 표시 |
|---------------------|-----------------|
| BEGINNER | 입문 |
| INTERMEDIATE | 중급 |
| ADVANCED | 고급 |

### 테스트 데이터 필요

백엔드에 테스트 + 문제 데이터가 INSERT되어 있어야 프론트엔드가 정상 동작합니다. 백엔드 DataInitializer 또는 data.sql이 준비되었는지 확인 필요.

### 에러 처리 패턴

기존 Phase 2와 동일한 패턴 사용:
```typescript
catch (err: unknown) {
  const axiosError = err as { response?: { data?: { message?: string } } };
  setError(axiosError.response?.data?.message || '기본 에러 메시지');
}
```
