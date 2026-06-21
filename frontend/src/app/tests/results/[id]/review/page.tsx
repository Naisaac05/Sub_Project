'use client';

import { useEffect, useMemo, useState, useRef } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import Header from '@/components/layout/Header';
import Footer from '@/components/layout/Footer';
import { useAuth } from '@/contexts/AuthContext';
import {
  getAiReviewSession,
  startAiReview,
  submitAiReviewAnswer,
  submitAiReviewAnswerStream,
  summarizeAiReviewQuestion,
  summarizeAiReviewSession,
} from '@/lib/ai-review';
import type { AiReviewSessionResponse } from '@/lib/types';
// 🧪 테스트 전용 (제거 시 본 import + 아래 렌더 라인 함께 삭제)
import { TestResetButton } from '@/components/ai-review/TestResetButton';
import { AlertCircle, ArrowLeft, Bot, CheckCircle, Clock3, FileText, Lightbulb, Loader2, Send, Target, User, Zap, X } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

const LABELS = {
  title: '\uC2A4\uB9C8\uD2B8 \uAC1C\uB150 \uBCF5\uC2B5',
  description:
    '\uD2C0\uB9B0 \uBB38\uC81C\uB97C \uAE30\uBC18\uC73C\uB85C \uACE0\uC815 \uAF2C\uB9AC\uC9C8\uBB38\uC744 \uC81C\uACF5\uD558\uACE0, \uBB38\uC81C\uBCC4 \uB300\uD654\uC640 \uC694\uC57D\uC73C\uB85C \uBCF5\uC2B5\uC744 \uC774\uC5B4\uAC11\uB2C8\uB2E4.',
  loading: '\uBCF5\uC2B5 \uC138\uC158\uC744 \uC900\uBE44\uD558\uB294 \uC911\uC785\uB2C8\uB2E4.',
  back: '\uACB0\uACFC\uB85C \uB3CC\uC544\uAC00\uAE30',
  wrongQuestions: '\uD2C0\uB9B0 \uBB38\uC81C',
  correct: '\uC815\uB2F5',
  selected: '\uB0B4 \uC120\uD0DD',
  answerPlaceholder: '\uB098\uC758 \uC0DD\uAC01\uC744 \uC9E7\uAC8C \uC801\uC5B4\uBCF4\uC138\uC694.',
  checkAnswer: '\uD655\uC778 \uC9C8\uBB38\uC5D0 \uB2F5\uD558\uAE30',
  freeQuestion: '\uAD81\uAE08\uD55C \uC810 \uC9C8\uBB38\uD558\uAE30',
  nextQuestion: '\uB2E4\uC74C \uBB38\uC81C\uB85C',
  completed: '\uBCF5\uC2B5 \uC644\uB8CC',
  summary: '\uBCF5\uC2B5 \uC694\uC57D',
  weaknessTags: '\uC57D\uC810 \uD0DC\uADF8',
  noWrong: '\uD2C0\uB9B0 \uBB38\uC81C\uAC00 \uC5C6\uC5B4 \uBCF5\uC2B5\uD560 \uB0B4\uC6A9\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.',
  currentConcept: '\uD604\uC7AC \uC810\uAC80 \uAC1C\uB150',
  learningStep: '\uD559\uC2B5 \uB2E8\uACC4',
  progress: '\uB0A8\uC740 \uC9C8\uBB38',
  nextAction: '\uB2E4\uC74C \uD589\uB3D9',
  answerGuide: '\uBA3C\uC800 \uB0B4 \uC120\uD0DD \uC774\uC720\uB97C \uC9E7\uAC8C \uC801\uACE0, \uB9C9\uD788\uBA74 \uAD81\uAE08\uD55C \uC810 \uC9C8\uBB38\uD558\uAE30\uB97C \uB20C\uB7EC\uBCF4\uC138\uC694.',
  currentQuestion: '\uD604\uC7AC \uBB38\uC81C',
  noMessages: '\uC774 \uBB38\uC81C\uC758 \uB300\uD654\uAC00 \uC544\uC9C1 \uC5C6\uC2B5\uB2C8\uB2E4.',
  viewConversation: '\uC804\uCCB4 \uB300\uD654 \uBCF4\uAE30',
  backToCurrent: '\uD604\uC7AC \uBB38\uC81C\uB85C \uB3CC\uC544\uAC00\uAE30',
  selectedQuestion: '\uC120\uD0DD\uD55C \uBB38\uC81C',
  thinking: '\uC0DD\uAC01\uD568',
  summarizeQuestion: '\uC774 \uBB38\uC81C \uC694\uC57D',
  summarizeAll: '\uC804\uCCB4 \uBCF5\uC2B5 \uB9AC\uD3EC\uD2B8',
  studySummary: '\uACF5\uBD80\uC6A9 \uC694\uC57D',
  reportReady: '\uC694\uC57D\uC774 \uC900\uBE44\uB410\uC2B5\uB2C8\uB2E4.',
  questionLimitReached: '\uC774 \uBB38\uC81C\uC758 \uC790\uC720 \uC9C8\uBB38 3\uAC1C\uB97C \uBAA8\uB450 \uC0AC\uC6A9\uD588\uC2B5\uB2C8\uB2E4. \uB2E4\uC74C \uBB38\uC81C\uB85C \uB118\uC5B4\uAC00\uC138\uC694.',
  slowAiNotice: '\uB85C\uCEEC AI \uC751\uB2F5\uC774 \uC870\uAE08 \uB2A6\uC5B4\uC9C0\uACE0 \uC788\uC5B4\uC694. \uB2F5\uBCC0\uC774 \uC800\uC7A5\uB418\uBA74 \uB300\uD654\uC5D0 \uC790\uB3D9\uC73C\uB85C \uBC18\uC601\uD569\uB2C8\uB2E4.',
  slowAiRecovered: '\uB2A6\uAC8C \uB3C4\uCC29\uD55C AI \uB2F5\uBCC0\uC744 \uBD88\uB7EC\uC654\uC2B5\uB2C8\uB2E4.',
  aiStateLoading: 'AI\uAC00 \uB2F5\uBCC0\uC744 \uC0DD\uC131\uD558\uB294 \uC911\uC785\uB2C8\uB2E4...',
  aiStateRetrying: '\uC751\uB2F5\uC774 \uC9C0\uC5F0\uB418\uACE0 \uC788\uC5B4\uC694. AI \uC11C\uBC84 \uC0C1\uD0DC\uB97C \uD655\uC778\uD558\uACE0 \uC788\uC2B5\uB2C8\uB2E4.',
  aiStateTimeout: 'AI \uC751\uB2F5\uC774 \uC2DC\uAC04\uC744 \uCD08\uACFC\uD574 \uC800\uC7A5\uB41C \uB2F5\uBCC0\uC744 \uD655\uC778\uD558\uACE0 \uC788\uC2B5\uB2C8\uB2E4.',
  aiStateFallback: '\uB300\uCCB4 \uB2F5\uBCC0\uC744 \uBCF4\uC5EC\uC904 \uC900\uBE44\uB97C \uD558\uACE0 \uC788\uC2B5\uB2C8\uB2E4.',
};

const MAX_AI_QUESTIONS_PER_WRONG_ANSWER = 3;
const SLOW_AI_NOTICE_DELAY_MS = 10000;
const STUDY_QUESTION_MODES = ['CHECK_QUESTION', 'EXPLANATION', 'NEXT_QUESTION', 'FREE_ANSWER'];
type AiRequestLifecycleState =
  | 'IDLE'
  | 'LOADING'
  | 'STREAMING'
  | 'SUCCESS'
  | 'ERROR'
  | 'FALLBACK'
  | 'CANCELLED';

function aiRequestStateLabel(state: AiRequestLifecycleState) {
  switch (state) {
    case 'LOADING':
    case 'STREAMING':
      return LABELS.aiStateLoading;
    case 'FALLBACK':
      return LABELS.aiStateFallback;
    default:
      return null;
  }
}

function resolveAiReviewError(error: unknown) {
  const maybeError = error as {
    code?: string;
    response?: { status?: number; data?: { message?: string } };
  };
  const serverMessage = maybeError.response?.data?.message;

  if (maybeError.code === 'ECONNABORTED') {
    return '\uB85C\uCEEC AI \uC751\uB2F5\uC774 \uC9C0\uC5F0\uB418\uC5B4 \uC2DC\uAC04\uC744 \uCD08\uACFC\uD588\uC2B5\uB2C8\uB2E4. \uC7A0\uC2DC \uD6C4 \uB2E4\uC2DC \uC2DC\uB3C4\uD558\uAC70\uB098 \uB2E4\uC74C \uBB38\uC81C\uB85C \uB118\uC5B4\uAC00\uC138\uC694.';
  }
  if (maybeError.response?.status === 422) {
    return '\uBCF5\uC2B5 \uB370\uC774\uD130 \uC911 \uBE44\uC5B4 \uC788\uB294 \uAC12\uC744 AI \uC11C\uBC84\uAC00 \uAC70\uC808\uD588\uC2B5\uB2C8\uB2E4. AI \uC11C\uBC84\uB97C \uB2E4\uC2DC \uC2DC\uC791\uD55C \uB4A4 \uC2DC\uB3C4\uD574\uBCF4\uC138\uC694.';
  }
  if (maybeError.response?.status && maybeError.response.status >= 500) {
    return serverMessage
      ? `\uBC31\uC5D4\uB4DC \uC624\uB958: ${serverMessage}`
      : '\uBC31\uC5D4\uB4DC\uAC00 \uBCF5\uC2B5 \uB2F5\uBCC0\uC744 \uCC98\uB9AC\uD558\uB294 \uC911 \uC624\uB958\uAC00 \uB0AC\uC2B5\uB2C8\uB2E4. \uBC31\uC5D4\uB4DC \uD130\uBBF8\uB110\uC758 \uC2A4\uD0DD \uD2B8\uB808\uC774\uC2A4\uB97C \uD655\uC778\uD574\uC8FC\uC138\uC694.';
  }
  return serverMessage || '\uB2F5\uBCC0\uC744 \uC81C\uCD9C\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.';
}

function isAiReviewTimeout(error: unknown) {
  return (error as { code?: string })?.code === 'ECONNABORTED';
}

function wait(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function learningStepLabel(mode: string | null | undefined, completed: boolean) {
  if (completed) {
    return '\uBCF5\uC2B5 \uC644\uB8CC';
  }
  switch (mode) {
    case 'CHECK_QUESTION':
    case 'NEXT_QUESTION':
      return '\uC120\uD0DD \uC774\uC720 \uC124\uBA85';
    case 'EXPLANATION':
      return '\uAC1C\uB150 \uBCF4\uC815';
    case 'FREE_ANSWER':
      return '\uC790\uC720 \uC9C8\uBB38 \uD655\uC778';
    case 'SYSTEM_SUMMARY':
      return '\uC694\uC57D';
    default:
      return '\uC120\uD0DD \uC774\uC720 \uC124\uBA85';
  }
}

function isVisibleConversationMessage(message: AiReviewSessionResponse['messages'][number]) {
  return message.mode !== 'QUESTION_SUMMARY' && message.mode !== 'REVIEW_REPORT';
}

function isStudyQuestionMessage(message: AiReviewSessionResponse['messages'][number]) {
  return message.role === 'AI' && STUDY_QUESTION_MODES.includes(message.mode ?? '');
}

function isUserFreeQuestionMessage(message: AiReviewSessionResponse['messages'][number]) {
  return message.role === 'USER' && message.mode === 'FREE_QUESTION';
}

function buildInitialQuestionPrompt(question: AiReviewSessionResponse['wrongQuestions'][number]) {
  return `이 문제의 핵심 개념이 무엇인지 먼저 짚어볼게요. "${question.selectedAnswer}"를 고른 이유와 정답이 되는 조건을 한 문장으로 설명해볼까요?`;
}

function secondsBetween(start: string | null | undefined, end: string | null | undefined) {
  if (!start || !end) {
    return null;
  }
  const startMs = new Date(start).getTime();
  const endMs = new Date(end).getTime();
  if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs) {
    return null;
  }
  return Math.max(0.1, (endMs - startMs) / 1000);
}

function inferResponseSeconds(
  message: AiReviewSessionResponse['messages'][number],
  messages: AiReviewSessionResponse['messages'],
  measuredDurations: Record<number, number>
) {
  if (message.role !== 'AI') {
    return null;
  }
  if (measuredDurations[message.id]) {
    return measuredDurations[message.id];
  }

  const messageIndex = messages.findIndex((item) => item.id === message.id);
  if (messageIndex < 0) {
    return null;
  }
  const previousUserMessage = [...messages.slice(0, messageIndex)]
    .reverse()
    .find((item) => item.role === 'USER' && item.questionId === message.questionId);
  return secondsBetween(previousUserMessage?.createdAt, message.createdAt);
}

function aiMetadataBadges(message: AiReviewSessionResponse['messages'][number]) {
  if (message.role !== 'AI') {
    return [];
  }
  return [
    message.aiRoute ? `route:${message.aiRoute}` : null,
    message.aiAnswerStyle ? `style:${message.aiAnswerStyle}` : null,
    message.aiCorrectionType && message.aiCorrectionType !== 'none' ? `fix:${message.aiCorrectionType}` : null,
    message.aiMatchedConceptId ? `concept:${message.aiMatchedConceptId}` : null,
    message.aiCandidateId ? `candidate:${message.aiCandidateId}` : null,
    typeof message.aiLatencyMs === 'number' ? `${message.aiLatencyMs}ms` : null,
    ...(message.aiQualityFlags ?? []).map((flag) => `quality:${flag}`),
  ].filter((item): item is string => Boolean(item));
}

export default function AiReviewPage() {
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const questionCardRef = useRef<HTMLDivElement>(null);
  const params = useParams();
  const router = useRouter();
  const { isLoggedIn, isLoading: authLoading } = useAuth();
  const testResultId = Number(params.id);

  const [session, setSession] = useState<AiReviewSessionResponse | null>(null);
  const [answer, setAnswer] = useState('');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [selectedQuestionId, setSelectedQuestionId] = useState<number | null>(null);
  const [messageDurations, setMessageDurations] = useState<Record<number, number>>({});
  const [questionSummaries, setQuestionSummaries] = useState<Record<number, string>>({});
  const [closedQuestionSummaryIds, setClosedQuestionSummaryIds] = useState<Set<number>>(new Set());
  const [overallStudyReport, setOverallStudyReport] = useState<string | null>(null);
  const [closedOverallReport, setClosedOverallReport] = useState(false);
  const [summaryLoading, setSummaryLoading] = useState<'question' | 'overall' | null>(null);
  const [optimisticUserMessage, setOptimisticUserMessage] = useState<string | null>(null);
  const [aiRequestState, setAiRequestState] = useState<AiRequestLifecycleState>('IDLE');
  const [streamingContent, setStreamingContent] = useState<string>('');

  const activeAbortControllerRef = useRef<AbortController | null>(null);
  const activeReaderRef = useRef<ReadableStreamDefaultReader<Uint8Array> | null>(null);
  const accumulatedContentRef = useRef<string>('');
  const batchTimerRef = useRef<number | null>(null);

  const cleanupActiveStream = () => {
    if (batchTimerRef.current) {
      window.clearInterval(batchTimerRef.current);
      batchTimerRef.current = null;
    }
    if (activeReaderRef.current) {
      try {
        activeReaderRef.current.cancel();
      } catch (e) {}
      activeReaderRef.current = null;
    }
    if (activeAbortControllerRef.current) {
      activeAbortControllerRef.current.abort();
      activeAbortControllerRef.current = null;
    }
  };

  useEffect(() => {
    return () => {
      cleanupActiveStream();
    };
  }, []);

  // Auto-scrolling is intentionally disabled per user request.
  // The user prefers the chat window to remain fixed when a new question or answer arrives.


  useEffect(() => {
    if (!authLoading && !isLoggedIn) {
      router.replace('/auth/login');
    }
  }, [authLoading, isLoggedIn, router]);

  useEffect(() => {
    if (authLoading || !isLoggedIn || Number.isNaN(testResultId)) {
      return;
    }

    const start = async () => {
      setLoading(true);
      setError(null);
      setNotice(null);
      try {
        const res = await startAiReview(testResultId);
        if (res.success) {
          setSession(res.data);
        } else {
          setError(res.message || LABELS.loading);
        }
      } catch {
        setError('\uBCF5\uC2B5 \uC138\uC158\uC744 \uC2DC\uC791\uD558\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.');
      } finally {
        setLoading(false);
      }
    };

    start();
  }, [authLoading, isLoggedIn, testResultId]);

  const activeQuestionId = useMemo(() => {
    const lastAi = [...(session?.messages ?? [])].reverse().find(isStudyQuestionMessage);
    return lastAi?.questionId ?? null;
  }, [session?.messages]);

  useEffect(() => {
    setSelectedQuestionId(null);
  }, [activeQuestionId]);

  const activeQuestionIndex = useMemo(() => {
    if (!session || activeQuestionId === null) {
      return 0;
    }
    const index = session.wrongQuestions.findIndex((question) => question.questionId === activeQuestionId);
    return index >= 0 ? index : 0;
  }, [activeQuestionId, session]);

  const activeWrongQuestion = useMemo(
    () => session?.wrongQuestions.find((question) => question.questionId === activeQuestionId) ?? session?.wrongQuestions[0] ?? null,
    [activeQuestionId, session?.wrongQuestions]
  );

  const messagesByQuestion = useMemo(() => {
    const grouped = new Map<number, AiReviewSessionResponse['messages']>();
    for (const message of session?.messages ?? []) {
      if (!message.questionId) {
        continue;
      }
      grouped.set(message.questionId, [...(grouped.get(message.questionId) ?? []), message]);
    }
    return grouped;
  }, [session?.messages]);

  useEffect(() => {
    const nextQuestionSummaries: Record<number, string> = {};
    let nextOverallReport: string | null = null;

    for (const message of session?.messages ?? []) {
      if (message.mode === 'QUESTION_SUMMARY' && message.questionId && !closedQuestionSummaryIds.has(message.questionId)) {
        nextQuestionSummaries[message.questionId] = message.content;
      }
      if (message.mode === 'REVIEW_REPORT' && !closedOverallReport) {
        nextOverallReport = message.content;
      }
    }

    setQuestionSummaries(nextQuestionSummaries);
    setOverallStudyReport(nextOverallReport);
  }, [session?.messages, closedQuestionSummaryIds, closedOverallReport]);

  const displayedQuestion = useMemo(
    () =>
      session?.wrongQuestions.find((question) => question.questionId === selectedQuestionId)
      ?? activeWrongQuestion
      ?? null,
    [activeWrongQuestion, selectedQuestionId, session?.wrongQuestions]
  );

  // Scroll to the question card when the displayed question changes (e.g. next question)
  useEffect(() => {
    if (displayedQuestion?.questionId != null && questionCardRef.current) {
      questionCardRef.current.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, [displayedQuestion?.questionId]);

  const isViewingCurrentQuestion = displayedQuestion?.questionId === activeWrongQuestion?.questionId;

  const displayedQuestionIndex = useMemo(() => {
    if (!session || !displayedQuestion) {
      return 0;
    }
    const index = session.wrongQuestions.findIndex((question) => question.questionId === displayedQuestion.questionId);
    return index >= 0 ? index : 0;
  }, [displayedQuestion, session]);

  const rawActiveMessages = displayedQuestion
    ? messagesByQuestion.get(displayedQuestion.questionId) ?? []
    : [];
  const activeMessages = rawActiveMessages.filter(isVisibleConversationMessage);
  const initialQuestionPrompt = displayedQuestion && !rawActiveMessages.some(isStudyQuestionMessage)
    ? buildInitialQuestionPrompt(displayedQuestion)
    : null;

  const latestAiMode = useMemo(
    () => [...(session?.messages ?? [])].reverse().find((message) => message.role === 'AI')?.mode ?? null,
    [session?.messages]
  );

  const usedQuestionCount = rawActiveMessages.filter(isUserFreeQuestionMessage).length;
  const remainingQuestionCount = session?.status === 'COMPLETED'
    ? 0
    : Math.max(0, MAX_AI_QUESTIONS_PER_WRONG_ANSWER - usedQuestionCount);
  const canAskMoreOnDisplayedQuestion = remainingQuestionCount > 0;
  const remainingQuestionPercent = Math.round(
    (remainingQuestionCount / MAX_AI_QUESTIONS_PER_WRONG_ANSWER) * 100
  );

  const refreshSessionAfterSlowAi = async (previousMessageCount: number) => {
    const currentSessionId = session?.sessionId;
    if (!currentSessionId) {
      return false;
    }

    for (let attempt = 0; attempt < 3; attempt += 1) {
      if (attempt > 0) {
        await wait(2000);
      }

      const res = await getAiReviewSession(currentSessionId);
      if (!res.success || !res.data) {
        continue;
      }

      setSession(res.data);
      if (res.data.messages.length > previousMessageCount) {
        setNotice(LABELS.slowAiRecovered);
        return true;
      }
    }

    setNotice(LABELS.slowAiNotice);
    return false;
  };

  const handleSubmit = async (mode: 'CHECK_ANSWER' | 'FREE_QUESTION' | 'NEXT_QUESTION', overrideAnswer?: string) => {
    const currentAnswer = (overrideAnswer !== undefined ? overrideAnswer : answer).trim();

    if (!session || submitting || session.status === 'COMPLETED') {
      return;
    }
    if (mode !== 'NEXT_QUESTION' && !currentAnswer) {
      return;
    }
    if (mode === 'FREE_QUESTION' && !canAskMoreOnDisplayedQuestion) {
      setError(LABELS.questionLimitReached);
      return;
    }

    cleanupActiveStream();

    const controller = new AbortController();
    activeAbortControllerRef.current = controller;

    setAnswer('');
    setOptimisticUserMessage(currentAnswer);
    setSubmitting(true);
    setAiRequestState('LOADING');
    setError(null);
    setNotice(null);
    setStreamingContent('');
    accumulatedContentRef.current = '';

    let hasReceivedChunk = false;
    const startedAt = performance.now();

    const slowNoticeTimer = window.setTimeout(() => {
      setNotice(LABELS.slowAiNotice);
    }, SLOW_AI_NOTICE_DELAY_MS);

    try {
      const res = await submitAiReviewAnswerStream(
        session.sessionId,
        currentAnswer,
        mode,
        mode === 'NEXT_QUESTION' ? activeWrongQuestion?.questionId : displayedQuestion?.questionId,
        controller.signal
      );

      const contentType = res.headers.get('content-type') || '';
      if (res.ok && contentType.includes('text/event-stream') && res.body) {
        setAiRequestState('STREAMING');
        const reader = res.body.getReader();
        activeReaderRef.current = reader;

        // 100ms 배치 업데이트 최적화로 UI 버벅임 최소화
        batchTimerRef.current = window.setInterval(() => {
          setStreamingContent(accumulatedContentRef.current);
        }, 100);

        const decoder = new TextDecoder();
        let buffer = '';

        try {
          while (true) {
            const { value, done } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            
            // UTF-8 멀티바이트 split 안정성을 위해 CRLF -> LF 정규화 후 \n\n split 진행
            const normalized = buffer.replace(/\r\n/g, '\n');
            const parts = normalized.split('\n\n');
            buffer = parts.pop() || '';

            for (const part of parts) {
              const lines = part.split('\n');
              for (const line of lines) {
                if (line.startsWith('data:')) {
                  const dataStr = line.substring(5).trim();
                  if (dataStr) {
                    const event = JSON.parse(dataStr);

                    if (event.type === 'chunk') {
                      hasReceivedChunk = true;
                      accumulatedContentRef.current += event.chunk || '';
                    } else if (event.type === 'done') {
                      cleanupActiveStream();
                      setStreamingContent('');

                      if (event.response && event.response.messages) {
                        const officialResponse = event.response as any;
                        setSession((current) => {
                          if (!current) return current;
                          const existingMessageIds = new Set(current.messages.map((m) => m.id));
                          const newMessages = (officialResponse.messages as any[]).filter((m: any) => !existingMessageIds.has(m.id));
                          return {
                            ...current,
                            status: officialResponse.completed ? 'COMPLETED' : current.status,
                            summary: officialResponse.summary ?? current.summary,
                            messages: [...current.messages, ...newMessages],
                          };
                        });
                      } else {
                        // 세션 데이터 유실 방지를 위한 Dynamic Reload Fallback
                        const reloadRes = await getAiReviewSession(session.sessionId);
                        if (reloadRes.success && reloadRes.data) {
                          setSession(reloadRes.data);
                        }
                      }

                      setAiRequestState('SUCCESS');
                      setSubmitting(false);
                      window.clearTimeout(slowNoticeTimer);
                      return;
                    } else if (event.type === 'error') {
                      cleanupActiveStream();
                      setStreamingContent('');
                      const streamErrorMsg = event.error || '답변 생성 에러가 수신되었습니다.';
                      
                      if (!hasReceivedChunk) {
                        throw new Error(streamErrorMsg);
                      } else {
                        setAiRequestState('ERROR');
                        setError(`답변 생성 중 오류가 발생했습니다: ${streamErrorMsg}`);
                        setSubmitting(false);
                        window.clearTimeout(slowNoticeTimer);
                        return;
                      }
                    }
                  }
                }
              }
            }
          }

          cleanupActiveStream();
          setStreamingContent('');
          if (!hasReceivedChunk) {
            throw new Error('스트림이 완료 이벤트 없이 종료되었습니다.');
          } else {
            setAiRequestState('ERROR');
            setError('답변 스트리밍 중 비정상적으로 중단되었습니다.');
            setSubmitting(false);
            window.clearTimeout(slowNoticeTimer);
            return;
          }
        } catch (streamReadErr: any) {
          cleanupActiveStream();
          setStreamingContent('');
          if (streamReadErr.name === 'AbortError') {
            setAiRequestState('CANCELLED');
            setSubmitting(false);
            window.clearTimeout(slowNoticeTimer);
            return;
          }
          if (!hasReceivedChunk) {
            throw streamReadErr;
          } else {
            setAiRequestState('ERROR');
            setError(`스트리밍 통신 오류가 발생했습니다: ${streamReadErr.message || streamReadErr}`);
            setSubmitting(false);
            window.clearTimeout(slowNoticeTimer);
            return;
          }
        }
      } else {
        throw new Error('스트리밍 응답이 올바르지 않습니다.');
      }
    } catch (err: any) {
      window.clearTimeout(slowNoticeTimer);
      cleanupActiveStream();
      setStreamingContent('');

      if (err.name === 'AbortError') {
        setAiRequestState('CANCELLED');
        setSubmitting(false);
        return;
      }

      // 이미 글자가 렌더링된 이후면 중복 차단을 위해 자동 복구 제출 금지
      if (hasReceivedChunk) {
        setAiRequestState('ERROR');
        setError(`스트리밍 통신 중 오류가 발생하여 중단되었습니다: ${err.message || err}`);
        setSubmitting(false);
        return;
      }

      // 첫 청크 수집 전이면 동기식 API로 즉시 자동 Fallback
      setAiRequestState('FALLBACK');
      try {
        const syncRes = await submitAiReviewAnswer(
          session.sessionId,
          currentAnswer,
          mode,
          mode === 'NEXT_QUESTION' ? activeWrongQuestion?.questionId : displayedQuestion?.questionId
        );
        if (syncRes.success) {
          const elapsedSeconds = Math.max(0.1, (performance.now() - startedAt) / 1000);
          setSession((current) => {
            if (!current) return current;
            const existingMessageIds = new Set(current.messages.map((m) => m.id));
            const newMessages = syncRes.data.messages.filter((m) => !existingMessageIds.has(m.id));
            return {
              ...current,
              status: syncRes.data.completed ? 'COMPLETED' : current.status,
              summary: syncRes.data.summary ?? current.summary,
              messages: [...current.messages, ...newMessages],
            };
          });
          const newAiMessageIds = syncRes.data.messages.filter((m) => m.role === 'AI').map((m) => m.id);
          if (newAiMessageIds.length > 0) {
            setMessageDurations((current) => {
              const next = { ...current };
              for (const mId of newAiMessageIds) {
                next[mId] = elapsedSeconds;
              }
              return next;
            });
          }
          setAiRequestState('SUCCESS');
        } else {
          setAiRequestState('ERROR');
          setError(syncRes.message || '답변 동기 복구 전송에 실패했습니다.');
        }
      } catch (syncErr: any) {
        setAiRequestState('ERROR');
        setError(resolveAiReviewError(syncErr));
        setAnswer(currentAnswer);
      } finally {
        setOptimisticUserMessage(null);
        setSubmitting(false);
      }
    }
  };

  const mergeNewMessages = (messages: AiReviewSessionResponse['messages']) => {
    setSession((current) => {
      if (!current) {
        return current;
      }

      const existingMessageIds = new Set(current.messages.map((message) => message.id));
      const newMessages = messages.filter((message) => !existingMessageIds.has(message.id));
      return {
        ...current,
        messages: [...current.messages, ...newMessages],
      };
    });
  };

  const handleSummarizeQuestion = async () => {
    if (!session || !displayedQuestion || summaryLoading) {
      return;
    }

    setClosedQuestionSummaryIds((prev) => {
      const next = new Set(prev);
      next.delete(displayedQuestion.questionId);
      return next;
    });

    setSummaryLoading('question');
    setError(null);
    setNotice(null);
    try {
      const res = await summarizeAiReviewQuestion(session.sessionId, displayedQuestion.questionId);
      if (res.success) {
        setQuestionSummaries((current) => ({
          ...current,
          [displayedQuestion.questionId]: res.data.summary,
        }));
        mergeNewMessages(res.data.messages);
      } else {
        setError(res.message || '\uBB38\uC81C \uC694\uC57D\uC744 \uB9CC\uB4E4\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.');
      }
    } catch (err) {
      if (isAiReviewTimeout(err)) {
        setNotice(LABELS.slowAiNotice);
        return;
      }
      setError(resolveAiReviewError(err));
    } finally {
      setSummaryLoading(null);
    }
  };

  const handleSummarizeAll = async () => {
    if (!session || summaryLoading) {
      return;
    }

    setClosedOverallReport(false);

    setSummaryLoading('overall');
    setError(null);
    setNotice(null);
    try {
      const res = await summarizeAiReviewSession(session.sessionId);
      if (res.success) {
        setOverallStudyReport(res.data.summary);
        mergeNewMessages(res.data.messages);
      } else {
        setError(res.message || '\uC804\uCCB4 \uBCF5\uC2B5 \uB9AC\uD3EC\uD2B8\uB97C \uB9CC\uB4E4\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.');
      }
    } catch (err) {
      if (isAiReviewTimeout(err)) {
        setNotice(LABELS.slowAiNotice);
        return;
      }
      setError(resolveAiReviewError(err));
    } finally {
      setSummaryLoading(null);
    }
  };

  if (authLoading || !isLoggedIn) {
    return null;
  }

  const aiRequestStateMessage = submitting ? aiRequestStateLabel(aiRequestState) : null;

  return (
    <>
      <Header />
      <main className="min-h-screen bg-gray-50">
        <section className="bg-[#07111f] pt-28 pb-14">
          <div className="mx-auto max-w-7xl px-6">
            <Link
              href="/tests/results"
              className="mb-6 inline-flex items-center gap-2 text-sm font-semibold text-gray-300 hover:text-white"
            >
              <ArrowLeft size={16} />
              {LABELS.back}
            </Link>
            <div className="flex items-center gap-4">
              <h1 className="text-3xl font-extrabold text-white sm:text-4xl">{LABELS.title}</h1>
              {/* 🧪 테스트 전용 (제거 시 본 라인 + 상단 import 함께 삭제) */}
              <TestResetButton testResultId={testResultId} />
            </div>
            <p className="mt-4 max-w-3xl break-keep text-base leading-7 text-gray-300">
              {LABELS.description}
            </p>
          </div>
        </section>

        <section className="mx-auto grid max-w-7xl gap-6 px-6 py-10 lg:grid-cols-[360px_1fr]">
          {loading ? (
            <div className="col-span-full flex flex-col items-center justify-center gap-3 py-24 text-gray-500">
              <Loader2 className="animate-spin text-blue-500" size={36} />
              <p className="text-sm font-medium">{LABELS.loading}</p>
            </div>
          ) : null}

          {!loading && error ? (
            <div className="col-span-full flex items-center justify-center gap-3 rounded-2xl border border-red-100 bg-white p-8 text-center font-semibold text-red-500">
              <AlertCircle size={20} />
              <span>{error}</span>
            </div>
          ) : null}

          {!loading && !error && notice ? (
            <div className="col-span-full flex items-center justify-center gap-3 rounded-2xl border border-amber-100 bg-amber-50 p-4 text-center text-sm font-semibold text-amber-700">
              <Clock3 size={18} />
              <span>{notice}</span>
            </div>
          ) : null}

          {!loading && !error && aiRequestStateMessage ? (
            <div className="col-span-full flex items-center justify-between gap-3 rounded-2xl border border-blue-100 bg-blue-50 p-4 text-sm font-semibold text-blue-700">
              <div className="flex items-center gap-3">
                <Loader2 size={18} className="animate-spin" />
                <span>{aiRequestStateMessage}</span>
              </div>
              {(aiRequestState === 'LOADING' || aiRequestState === 'STREAMING') && (
                <button
                  type="button"
                  onClick={() => {
                    cleanupActiveStream();
                    setAiRequestState('CANCELLED');
                    setSubmitting(false);
                    setError('답변 생성이 사용자에 의해 중단되었습니다.');
                  }}
                  className="inline-flex items-center gap-1 rounded-lg border border-blue-200 bg-white px-3 py-1.5 text-xs font-bold text-blue-700 transition-colors hover:bg-blue-100"
                >
                  <X size={12} />
                  <span>중단하기</span>
                </button>
              )}
            </div>
          ) : null}

          {!loading && !error && session ? (
            <>
              <aside className="rounded-2xl border border-gray-200 bg-white p-5">
                <h2 className="mb-4 text-lg font-extrabold text-gray-950">{LABELS.wrongQuestions}</h2>
                {session.wrongQuestions.length === 0 ? (
                  <p className="break-keep text-sm leading-6 text-gray-500">{LABELS.noWrong}</p>
                ) : (
                  <div className="space-y-3">
                    {session.wrongQuestions.map((question, index) => {
                      const isActive = question.questionId === displayedQuestion?.questionId;
                      const isCurrent = question.questionId === activeWrongQuestion?.questionId;
                      const isPast = index < activeQuestionIndex;
                      return (
                      <button
                        key={question.questionId}
                        type="button"
                        onClick={() => setSelectedQuestionId(question.questionId)}
                        className={`rounded-xl border p-4 ${
                          isActive
                            ? 'border-blue-200 bg-blue-50'
                            : isPast
                              ? 'border-emerald-100 bg-emerald-50'
                            : 'border-gray-100 bg-gray-50'
                        } text-left transition-colors hover:border-blue-200 hover:bg-blue-50`}
                      >
                        <div className="mb-2 flex items-center justify-between gap-2">
                          <span className="text-xs font-bold text-blue-600">Q{index + 1}</span>
                          <span className="rounded-full bg-white px-2 py-0.5 text-xs font-semibold text-gray-500">
                            {isCurrent ? '\uD604\uC7AC' : question.area}
                          </span>
                        </div>
                        <p className="break-keep text-sm font-semibold leading-5 text-gray-800">
                          {question.content}
                        </p>
                        <p className="mt-3 text-xs leading-5 text-gray-500">
                          {LABELS.selected}: {question.selectedAnswer}
                        </p>
                        <p className="text-xs leading-5 text-gray-500">
                          {LABELS.correct}: {question.correctAnswer}
                        </p>
                      </button>
                    );
                    })}
                  </div>
                )}
              </aside>

              <div className="rounded-2xl border border-gray-200 bg-white p-5 sm:p-7">
                <div className="mb-6 grid gap-3 md:grid-cols-3">
                  <div className="rounded-xl border border-blue-100 bg-blue-50 p-4">
                    <div className="mb-2 flex items-center gap-2 text-xs font-bold text-blue-700">
                      <Target size={15} />
                      {LABELS.currentConcept}
                    </div>
                    <p className="break-keep text-sm font-extrabold text-gray-950">
                      {activeWrongQuestion?.area ?? session.courseKey}
                    </p>
                    <p className="mt-1 line-clamp-2 break-keep text-xs leading-5 text-blue-900">
                      {activeWrongQuestion?.content ?? LABELS.noWrong}
                    </p>
                  </div>
                  <div className="rounded-xl border border-amber-100 bg-amber-50 p-4">
                    <div className="mb-2 flex items-center gap-2 text-xs font-bold text-amber-700">
                      <Lightbulb size={15} />
                      {LABELS.learningStep}
                    </div>
                    <p className="text-sm font-extrabold text-gray-950">
                      {learningStepLabel(latestAiMode, session.status === 'COMPLETED')}
                    </p>
                    <p className="mt-1 break-keep text-xs leading-5 text-amber-900">
                      {LABELS.answerGuide}
                    </p>
                  </div>
                  <div className="rounded-xl border border-emerald-100 bg-emerald-50 p-4">
                    <div className="mb-2 flex items-center gap-2 text-xs font-bold text-emerald-700">
                      <Clock3 size={15} />
                      {LABELS.progress}
                    </div>
                    <div className="flex items-end justify-between gap-3">
                      <p className="text-2xl font-extrabold text-gray-950 flex items-center gap-1">
                        {Array.from({length: remainingQuestionCount}).map((_, i) => (
                          <Zap key={i} size={20} className="fill-amber-400 text-amber-500" />
                        ))}
                      </p>
                      <p className="text-xs font-semibold text-emerald-800">
                        질문 기회 {remainingQuestionCount}번 남았어요!
                      </p>
                    </div>
                  </div>
                </div>

                {displayedQuestion ? (
                  <div ref={questionCardRef} className="mb-5 rounded-xl border border-blue-100 bg-white p-4">
                    <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                      <span className="text-xs font-bold text-blue-600">
                        {isViewingCurrentQuestion ? LABELS.currentQuestion : LABELS.selectedQuestion} · Q{displayedQuestionIndex + 1}
                      </span>
                      {!isViewingCurrentQuestion ? (
                        <button
                          type="button"
                          onClick={() => setSelectedQuestionId(null)}
                          className="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-bold text-blue-700 hover:bg-blue-100"
                        >
                          {LABELS.backToCurrent}
                        </button>
                      ) : null}
                    </div>
                    <p className="break-keep text-base font-extrabold leading-7 text-gray-950">
                      {displayedQuestion.content}
                    </p>
                    <div className="mt-3 grid gap-2 text-xs leading-5 text-gray-600 sm:grid-cols-2">
                      <p>{LABELS.selected}: {displayedQuestion.selectedAnswer}</p>
                      <p>{LABELS.correct}: {displayedQuestion.correctAnswer}</p>
                    </div>
                    <div className="mt-4 flex flex-wrap gap-2">
                      <button
                        type="button"
                        onClick={handleSummarizeQuestion}
                        disabled={summaryLoading !== null}
                        className="inline-flex items-center justify-center gap-2 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs font-bold text-emerald-700 transition-colors hover:bg-emerald-100 disabled:cursor-not-allowed disabled:border-gray-200 disabled:bg-gray-100 disabled:text-gray-400"
                      >
                        {summaryLoading === 'question' ? <Loader2 size={14} className="animate-spin" /> : <FileText size={14} />}
                        {LABELS.summarizeQuestion}
                      </button>
                      <button
                        type="button"
                        onClick={handleSummarizeAll}
                        disabled={summaryLoading !== null}
                        className="inline-flex items-center justify-center gap-2 rounded-xl border border-gray-200 bg-gray-50 px-3 py-2 text-xs font-bold text-gray-700 transition-colors hover:bg-gray-100 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
                      >
                        {summaryLoading === 'overall' ? <Loader2 size={14} className="animate-spin" /> : <Target size={14} />}
                        {LABELS.summarizeAll}
                      </button>
                    </div>
                  </div>
                ) : null}

                {displayedQuestion && questionSummaries[displayedQuestion.questionId] ? (
                  <div className="mb-5 rounded-xl border border-emerald-100 bg-emerald-50 p-4">
                    <div className="mb-2 flex items-center justify-between gap-2 text-sm font-extrabold text-emerald-800">
                      <div className="flex items-center gap-2">
                        <FileText size={16} />
                        {LABELS.studySummary}
                      </div>
                      <button 
                        onClick={() => {
                          setClosedQuestionSummaryIds((prev) => new Set(prev).add(displayedQuestion.questionId));
                        }}
                        className="rounded hover:bg-emerald-200/50 p-1 transition-colors"
                      >
                        <X size={16} />
                      </button>
                    </div>
                    <div className="prose prose-sm prose-emerald max-w-none text-emerald-950">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {questionSummaries[displayedQuestion.questionId]}
                      </ReactMarkdown>
                    </div>
                  </div>
                ) : null}

                {overallStudyReport ? (
                  <div className="mb-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
                    <div className="mb-2 flex items-center justify-between gap-2 text-sm font-extrabold text-slate-900">
                      <div className="flex items-center gap-2">
                        <Target size={16} />
                        {LABELS.summarizeAll}
                      </div>
                      <button 
                        onClick={() => setClosedOverallReport(true)}
                        className="rounded hover:bg-slate-200/50 p-1 transition-colors"
                      >
                        <X size={16} />
                      </button>
                    </div>
                    <div className="prose prose-sm prose-slate max-w-none text-slate-700">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {overallStudyReport}
                      </ReactMarkdown>
                    </div>
                  </div>
                ) : null}

                <div className="space-y-4">
                  {initialQuestionPrompt ? (
                    <div className="flex gap-3 justify-start">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
                        <Bot size={18} />
                      </div>
                      <div className="max-w-[82%]">
                        <div className="rounded-2xl bg-gray-100 px-4 py-3 text-sm leading-6 text-gray-800">
                          <p className="whitespace-pre-line break-keep">{initialQuestionPrompt}</p>
                        </div>
                      </div>
                    </div>
                  ) : null}
                  {!initialQuestionPrompt && activeMessages.length === 0 ? (
                    <div className="flex flex-col items-center gap-4 py-8">
                      <div className="text-sm font-semibold text-gray-400">
                        AI에게 먼저 질문해보세요!
                      </div>
                      <div className="flex flex-wrap justify-center gap-2">
                        <button
                          onClick={() => handleSubmit('FREE_QUESTION', '이 문제가 왜 틀렸는지 설명해줘')}
                          className="rounded-full border border-blue-200 bg-blue-50 px-4 py-2 text-sm font-medium text-blue-600 hover:bg-blue-100 transition-colors"
                        >
                          이 문제가 왜 틀렸는지 설명해줘
                        </button>
                        <button
                          onClick={() => handleSubmit('FREE_QUESTION', '정답의 핵심 개념만 짧게 요약해줘')}
                          className="rounded-full border border-blue-200 bg-blue-50 px-4 py-2 text-sm font-medium text-blue-600 hover:bg-blue-100 transition-colors"
                        >
                          핵심 개념만 짧게 요약해줘
                        </button>
                      </div>
                    </div>
                  ) : null}
                  {activeMessages.map((message) => {
                    const isAi = message.role === 'AI';
                    const duration = inferResponseSeconds(message, rawActiveMessages, messageDurations);
                    const metadataBadges = aiMetadataBadges(message);
                    return (
                      <div
                        key={message.id}
                        className={`flex gap-3 ${isAi ? 'justify-start' : 'justify-end'}`}
                      >
                        {isAi ? (
                          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
                            <Bot size={18} />
                          </div>
                        ) : null}
                        <div className={`max-w-[82%] ${isAi ? '' : 'text-right'}`}>
                          {isAi && duration ? (
                            <div className="mb-1 flex items-center gap-1 text-xs font-semibold text-gray-400">
                              <Clock3 size={12} />
                              {duration.toFixed(1)}초 동안 {LABELS.thinking}
                            </div>
                          ) : null}
                          <div
                            className={`rounded-2xl px-4 py-3 text-sm leading-6 ${
                              isAi
                                ? 'bg-gray-100 text-gray-800'
                                : 'bg-blue-600 text-white'
                            }`}
                          >
                            {isAi ? (
                              <div className="prose prose-sm max-w-none prose-p:my-1 prose-pre:my-2 prose-pre:rounded-xl prose-pre:border prose-pre:border-slate-200 prose-pre:bg-slate-50 prose-pre:p-3 prose-pre:text-slate-700 prose-code:text-blue-700 prose-code:before:content-none prose-code:after:content-none">
                                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                  {message.content}
                                </ReactMarkdown>
                              </div>
                            ) : (
                              <p className="whitespace-pre-line break-keep">{message.content}</p>
                            )}
                          </div>
                        </div>
                        {!isAi ? (
                          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gray-900 text-white">
                            <User size={18} />
                          </div>
                        ) : null}
                      </div>
                    );
                  })}
                  {submitting && optimisticUserMessage && (
                    <div className="flex gap-3 justify-end">
                      <div className="max-w-[82%] text-right">
                        <div className="rounded-2xl bg-blue-600 px-4 py-3 text-sm leading-6 text-white inline-block text-left">
                          <p className="whitespace-pre-line break-keep">{optimisticUserMessage}</p>
                        </div>
                      </div>
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gray-900 text-white">
                        <User size={18} />
                      </div>
                    </div>
                  )}
                  {streamingContent && (
                    <div className="flex gap-3 justify-start">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
                        <Bot size={18} />
                      </div>
                      <div className="max-w-[82%]">
                        <div className="rounded-2xl px-4 py-3 text-sm leading-6 bg-gray-100 text-gray-800">
                          <div className="prose prose-sm max-w-none prose-p:my-1 prose-pre:my-2 prose-pre:rounded-xl prose-pre:border prose-pre:border-slate-200 prose-pre:bg-slate-50 prose-pre:p-3 prose-pre:text-slate-700 prose-code:text-blue-700 prose-code:before:content-none prose-code:after:content-none">
                            <ReactMarkdown remarkPlugins={[remarkGfm]}>
                              {streamingContent}
                            </ReactMarkdown>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}
                  {submitting && !streamingContent && (
                    <div className="flex gap-3 justify-start">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
                        <Bot size={18} />
                      </div>
                      <div className="max-w-[82%]">
                        <div className="rounded-2xl bg-gray-100 px-4 py-4 flex gap-1 items-center h-[44px]">
                          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '-0.3s' }}></div>
                          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '-0.15s' }}></div>
                          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                        </div>
                      </div>
                    </div>
                  )}
                  <div ref={messagesEndRef} />
                </div>

                {session.status === 'COMPLETED' ? (
                  <div className="mt-8 rounded-2xl border border-emerald-100 bg-emerald-50 p-5">
                    <div className="mb-3 flex items-center gap-2 text-emerald-700">
                      <CheckCircle size={18} />
                      <h2 className="font-extrabold">{LABELS.completed}</h2>
                    </div>
                    <p className="break-keep text-sm leading-6 text-emerald-900">
                      {session.summary}
                    </p>
                    {session.weaknessTags ? (
                      <p className="mt-3 text-sm font-semibold text-emerald-700">
                        {LABELS.weaknessTags}: {session.weaknessTags}
                      </p>
                    ) : null}
                  </div>
                ) : (
                  <div className="mt-8 space-y-3">
                    <textarea
                      value={answer}
                      onChange={(event) => setAnswer(event.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          if (answer.trim() && !submitting) {
                            handleSubmit('FREE_QUESTION');
                          }
                        }
                      }}
                      maxLength={700}
                      rows={3}
                      className="min-h-[88px] w-full resize-none rounded-xl border border-gray-200 px-4 py-3 text-sm outline-none focus:border-blue-400"
                      placeholder={LABELS.answerPlaceholder}
                    />
                    <div className="grid gap-2 sm:grid-cols-3">
                      <button
                        onClick={() => handleSubmit('CHECK_ANSWER')}
                        disabled={!answer.trim() || submitting}
                        className="inline-flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-3 text-sm font-bold text-white transition-colors hover:bg-blue-500 disabled:cursor-not-allowed disabled:bg-gray-300"
                      >
                        {submitting ? <Loader2 size={16} className="animate-spin" /> : <Send size={16} />}
                        {LABELS.checkAnswer}
                      </button>
                      <button
                        onClick={() => handleSubmit('FREE_QUESTION')}
                        disabled={!answer.trim() || submitting || !canAskMoreOnDisplayedQuestion}
                        className="inline-flex items-center justify-center gap-2 rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm font-bold text-blue-700 transition-colors hover:bg-blue-100 disabled:cursor-not-allowed disabled:border-gray-200 disabled:bg-gray-100 disabled:text-gray-400"
                      >
                        <Lightbulb size={16} />
                        {LABELS.freeQuestion}
                      </button>
                      <button
                        onClick={() => handleSubmit('NEXT_QUESTION')}
                        disabled={submitting || !isViewingCurrentQuestion}
                        className="inline-flex items-center justify-center rounded-xl border border-gray-200 bg-white px-4 py-3 text-sm font-bold text-gray-700 transition-colors hover:bg-gray-50 disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-400"
                      >
                        {LABELS.nextQuestion}
                      </button>
                    </div>
                    <p className="break-keep text-xs leading-5 text-gray-500">
                      {LABELS.answerGuide}
                      {!canAskMoreOnDisplayedQuestion ? ` ${LABELS.questionLimitReached}` : ''}
                      {!isViewingCurrentQuestion ? ` ${LABELS.nextQuestion}는 ${LABELS.currentQuestion}에서만 사용할 수 있습니다.` : ''}
                    </p>
                  </div>
                )}
              </div>
            </>
          ) : null}
        </section>
      </main>
      <Footer />
    </>
  );
}
