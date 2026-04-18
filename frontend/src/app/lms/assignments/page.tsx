'use client';

import { useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  CheckCircle2,
  ClipboardList,
  Code2,
  ExternalLink,
  Loader2,
  MessageSquareText,
  Plus,
  Send,
} from 'lucide-react';
import { useAuth } from '@/contexts/AuthContext';
import { createAssignment, feedbackAssignment, getAssignments, submitAssignment } from '@/lib/lms';
import { getMyMatchingsAsMentee, getMyMatchingsAsMentor } from '@/lib/matching';
import type { AssignmentCreateRequest, AssignmentResponse, AssignmentType } from '@/lib/lms-types';
import type { MatchingResponse } from '@/lib/types';

type FilterType = 'ALL' | AssignmentType;

const TYPE_META: Record<AssignmentType, { label: string; icon: typeof ClipboardList; color: string }> = {
  TASK: {
    label: '과제',
    icon: ClipboardList,
    color: 'bg-blue-500/10 text-blue-400',
  },
  CODE_REVIEW: {
    label: '코드리뷰',
    icon: Code2,
    color: 'bg-violet-500/10 text-violet-400',
  },
};

export default function AssignmentsPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const queryMatchingId = Number(searchParams.get('matchingId'));
  const { user, isLoading: authLoading } = useAuth();
  const isMentor = user?.role === 'MENTOR';

  const [resolvedMatchingId, setResolvedMatchingId] = useState<number | null>(
    Number.isFinite(queryMatchingId) && queryMatchingId > 0 ? queryMatchingId : null
  );
  const [assignments, setAssignments] = useState<AssignmentResponse[]>([]);
  const [filter, setFilter] = useState<FilterType>('ALL');
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [pageMessage, setPageMessage] = useState('');
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState({
    type: 'TASK' as AssignmentType,
    title: '',
    description: '',
    dueDate: '',
    referenceUrls: '',
  });
  const [submissionDrafts, setSubmissionDrafts] = useState<Record<number, { submissionUrl: string; submissionNote: string }>>({});
  const [feedbackDrafts, setFeedbackDrafts] = useState<Record<number, { feedbackContent: string; grade: string }>>({});

  useEffect(() => {
    if (authLoading) {
      return;
    }

    if (resolvedMatchingId) {
      return;
    }

    if (!user) {
      setPageMessage('로그인 후 과제와 코드리뷰를 확인할 수 있습니다.');
      setLoading(false);
      return;
    }

    const resolveMatching = async () => {
      try {
        const response = user.role === 'MENTOR' ? await getMyMatchingsAsMentor() : await getMyMatchingsAsMentee();
        const activeMatching = response.data.find(
          (matching: MatchingResponse) => matching.status === 'ACCEPTED' || matching.status === 'TRIAL'
        );

        if (activeMatching) {
          setResolvedMatchingId(activeMatching.id);
          router.replace(`/lms/assignments?matchingId=${activeMatching.id}`);
          return;
        }

        setPageMessage('연결된 LMS 매칭이 아직 없어 과제와 코드리뷰를 불러올 수 없습니다.');
      } catch (error) {
        console.error(error);
        setPageMessage('매칭 정보를 불러오지 못해 과제 화면을 열 수 없습니다.');
      } finally {
        setLoading(false);
      }
    };

    void resolveMatching();
  }, [authLoading, resolvedMatchingId, router, user]);

  const loadAssignments = async (matchingId: number, nextFilter: FilterType) => {
    const response = await getAssignments(matchingId, nextFilter === 'ALL' ? undefined : nextFilter);
    setAssignments(response.data.data || []);
  };

  useEffect(() => {
    if (!resolvedMatchingId) {
      return;
    }

    const fetchData = async () => {
      setLoading(true);
      setPageMessage('');
      try {
        await loadAssignments(resolvedMatchingId, filter);
      } catch (error) {
        console.error(error);
        setPageMessage('과제 목록을 불러오지 못했습니다.');
      } finally {
        setLoading(false);
      }
    };

    void fetchData();
  }, [filter, resolvedMatchingId]);

  const counts = useMemo(
    () => ({
      ALL: assignments.length,
      TASK: assignments.filter((assignment) => assignment.type === 'TASK').length,
      CODE_REVIEW: assignments.filter((assignment) => assignment.type === 'CODE_REVIEW').length,
    }),
    [assignments]
  );

  const handleCreate = async () => {
    if (!resolvedMatchingId || !createForm.title.trim()) {
      return;
    }

    setSubmitting(true);
    try {
      const payload: AssignmentCreateRequest = {
        matchingId: resolvedMatchingId,
        type: createForm.type,
        title: createForm.title.trim(),
        description: createForm.description.trim() || undefined,
        dueDate: createForm.dueDate || undefined,
        referenceUrls: createForm.referenceUrls
          .split('\n')
          .map((value) => value.trim())
          .filter(Boolean),
      };

      await createAssignment(payload);
      setCreateForm({
        type: 'TASK',
        title: '',
        description: '',
        dueDate: '',
        referenceUrls: '',
      });
      setShowCreateForm(false);
      await loadAssignments(resolvedMatchingId, filter);
    } catch (error) {
      console.error(error);
      alert('과제를 등록하지 못했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmitAssignment = async (assignmentId: number) => {
    const draft = submissionDrafts[assignmentId];
    if (!draft?.submissionUrl.trim()) {
      return;
    }

    setSubmitting(true);
    try {
      await submitAssignment(assignmentId, {
        submissionUrl: draft.submissionUrl.trim(),
        submissionNote: draft.submissionNote.trim() || undefined,
      });
      if (resolvedMatchingId) {
        await loadAssignments(resolvedMatchingId, filter);
      }
    } catch (error) {
      console.error(error);
      alert('과제 제출에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleFeedback = async (assignmentId: number) => {
    const draft = feedbackDrafts[assignmentId];
    if (!draft?.feedbackContent.trim()) {
      return;
    }

    setSubmitting(true);
    try {
      await feedbackAssignment(assignmentId, {
        feedbackContent: draft.feedbackContent.trim(),
        grade: draft.grade.trim() || undefined,
      });
      if (resolvedMatchingId) {
        await loadAssignments(resolvedMatchingId, filter);
      }
    } catch (error) {
      console.error(error);
      alert('피드백 저장에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <Loader2 size={32} className="animate-spin text-blue-400" />
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">과제 / 코드리뷰</h1>
          <p className="mt-1 text-gray-400">현재 매칭에 연결된 과제와 코드리뷰를 확인하고 진행하세요.</p>
        </div>
        {isMentor ? (
          <button
            type="button"
            onClick={() => setShowCreateForm((prev) => !prev)}
            className="inline-flex items-center gap-2 rounded-xl bg-blue-500/10 px-4 py-2 text-sm font-medium text-blue-400 transition-colors hover:bg-blue-500/20"
          >
            <Plus size={16} />
            {showCreateForm ? '등록 폼 닫기' : '새 과제 등록'}
          </button>
        ) : null}
      </div>

      <div className="flex gap-2">
        {(['ALL', 'TASK', 'CODE_REVIEW'] as FilterType[]).map((value) => {
          const active = filter === value;
          const label = value === 'ALL' ? '전체' : TYPE_META[value].label;
          return (
            <button
              key={value}
              type="button"
              onClick={() => setFilter(value)}
              className={`rounded-xl px-4 py-2 text-sm font-medium transition-colors ${
                active ? 'bg-blue-500/20 text-blue-400' : 'text-gray-400 hover:bg-white/5 hover:text-white'
              }`}
            >
              {label} {counts[value]}
            </button>
          );
        })}
      </div>

      {pageMessage ? <p className="text-sm text-red-400">{pageMessage}</p> : null}

      {isMentor && showCreateForm ? (
        <section className="rounded-2xl border border-white/5 bg-[#0f1420] p-6">
          <h2 className="text-lg font-semibold text-white">과제 등록</h2>
          <div className="mt-5 grid gap-4 md:grid-cols-2">
            <div>
              <label className="text-sm text-gray-400">유형</label>
              <select
                value={createForm.type}
                onChange={(event) => setCreateForm((prev) => ({ ...prev, type: event.target.value as AssignmentType }))}
                className="mt-2 w-full rounded-xl border border-white/10 bg-[#131a2b] px-4 py-3 text-white outline-none"
              >
                <option value="TASK">과제</option>
                <option value="CODE_REVIEW">코드리뷰</option>
              </select>
            </div>
            <div>
              <label className="text-sm text-gray-400">마감일</label>
              <input
                type="date"
                value={createForm.dueDate}
                onChange={(event) => setCreateForm((prev) => ({ ...prev, dueDate: event.target.value }))}
                className="mt-2 w-full rounded-xl border border-white/10 bg-[#131a2b] px-4 py-3 text-white outline-none"
              />
            </div>
          </div>

          <div className="mt-4">
            <label className="text-sm text-gray-400">제목</label>
            <input
              type="text"
              value={createForm.title}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, title: event.target.value }))}
              placeholder="예: REST API 설계 과제"
              className="mt-2 w-full rounded-xl border border-white/10 bg-[#131a2b] px-4 py-3 text-white outline-none"
            />
          </div>

          <div className="mt-4">
            <label className="text-sm text-gray-400">설명</label>
            <textarea
              value={createForm.description}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, description: event.target.value }))}
              rows={5}
              placeholder="과제 설명을 입력하세요."
              className="mt-2 w-full rounded-xl border border-white/10 bg-[#131a2b] px-4 py-3 text-white outline-none"
            />
          </div>

          <div className="mt-4">
            <label className="text-sm text-gray-400">참고 링크</label>
            <textarea
              value={createForm.referenceUrls}
              onChange={(event) => setCreateForm((prev) => ({ ...prev, referenceUrls: event.target.value }))}
              rows={3}
              placeholder="한 줄에 하나씩 URL을 입력하세요."
              className="mt-2 w-full rounded-xl border border-white/10 bg-[#131a2b] px-4 py-3 text-white outline-none"
            />
          </div>

          <button
            type="button"
            onClick={handleCreate}
            disabled={submitting || !createForm.title.trim()}
            className="mt-5 inline-flex items-center gap-2 rounded-xl bg-blue-500/20 px-4 py-2.5 text-sm font-medium text-blue-300 transition-colors hover:bg-blue-500/30 disabled:opacity-50"
          >
            <Plus size={16} />
            등록하기
          </button>
        </section>
      ) : null}

      {assignments.length === 0 ? (
        <div className="py-20 text-center">
          <ClipboardList size={48} className="mx-auto mb-4 text-gray-600" />
          <p className="text-gray-400">아직 등록된 과제나 코드리뷰가 없습니다.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {assignments.map((assignment) => {
            const meta = TYPE_META[assignment.type];
            const Icon = meta.icon;
            const submissionDraft = submissionDrafts[assignment.id] || { submissionUrl: '', submissionNote: '' };
            const feedbackDraft = feedbackDrafts[assignment.id] || { feedbackContent: '', grade: '' };

            return (
              <article key={assignment.id} className="rounded-2xl border border-white/5 bg-[#0f1420] p-6">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                  <div className="flex-1">
                    <div className="mb-3 flex flex-wrap items-center gap-2">
                      <span className={`inline-flex items-center gap-2 rounded-lg px-2.5 py-1 text-xs font-medium ${meta.color}`}>
                        <Icon size={14} />
                        {meta.label}
                      </span>
                      <span className="rounded-lg bg-white/5 px-2.5 py-1 text-xs font-medium text-gray-400">
                        {assignment.status}
                      </span>
                    </div>
                    <h2 className="text-lg font-semibold text-white">{assignment.title}</h2>
                    {assignment.description ? (
                      <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-gray-300">{assignment.description}</p>
                    ) : null}
                  </div>

                  <div className="min-w-[220px] rounded-2xl border border-white/5 bg-[#131a2b] p-4">
                    <div className="text-sm text-gray-400">
                      <div className="flex items-center gap-2">
                        <CheckCircle2 size={14} className="text-blue-400" />
                        등록일 {new Date(assignment.createdAt).toLocaleDateString('ko-KR')}
                      </div>
                      <div className="mt-2 flex items-center gap-2">
                        <CheckCircle2 size={14} className="text-violet-400" />
                        {assignment.dueDate ? `마감일 ${assignment.dueDate}` : '마감일 없음'}
                      </div>
                    </div>
                  </div>
                </div>

                {assignment.referenceUrls.length > 0 ? (
                  <div className="mt-4 rounded-2xl border border-white/5 bg-[#131a2b] p-4">
                    <p className="mb-2 text-sm font-medium text-white">참고 링크</p>
                    <div className="space-y-2">
                      {assignment.referenceUrls.map((url) => (
                        <a
                          key={url}
                          href={url}
                          target="_blank"
                          rel="noreferrer"
                          className="flex items-center gap-2 text-sm text-blue-400 hover:text-blue-300"
                        >
                          <ExternalLink size={14} />
                          {url}
                        </a>
                      ))}
                    </div>
                  </div>
                ) : null}

                {assignment.submission ? (
                  <div className="mt-4 rounded-2xl border border-emerald-500/20 bg-emerald-500/10 p-4">
                    <p className="text-sm font-medium text-emerald-300">제출 완료</p>
                    <a
                      href={assignment.submission.submissionUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="mt-2 inline-flex items-center gap-2 text-sm text-white underline underline-offset-4"
                    >
                      <ExternalLink size={14} />
                      제출 링크 열기
                    </a>
                    {assignment.submission.submissionNote ? (
                      <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-gray-100">{assignment.submission.submissionNote}</p>
                    ) : null}
                    {assignment.submission.feedbackContent ? (
                      <div className="mt-4 rounded-xl border border-white/10 bg-black/20 p-4">
                        <p className="flex items-center gap-2 text-sm font-medium text-white">
                          <MessageSquareText size={14} />
                          멘토 피드백
                        </p>
                        <p className="mt-2 whitespace-pre-wrap text-sm leading-6 text-gray-200">
                          {assignment.submission.feedbackContent}
                        </p>
                        {assignment.submission.grade ? (
                          <p className="mt-2 text-sm text-emerald-200">평가: {assignment.submission.grade}</p>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                ) : null}

                {!isMentor && assignment.status === 'ASSIGNED' ? (
                  <div className="mt-4 rounded-2xl border border-white/5 bg-[#131a2b] p-4">
                    <p className="mb-3 text-sm font-medium text-white">과제 제출</p>
                    <input
                      type="url"
                      value={submissionDraft.submissionUrl}
                      onChange={(event) =>
                        setSubmissionDrafts((prev) => ({
                          ...prev,
                          [assignment.id]: { ...submissionDraft, submissionUrl: event.target.value },
                        }))
                      }
                      placeholder="제출 링크를 입력하세요."
                      className="w-full rounded-xl border border-white/10 bg-[#0f1420] px-4 py-3 text-white outline-none"
                    />
                    <textarea
                      value={submissionDraft.submissionNote}
                      onChange={(event) =>
                        setSubmissionDrafts((prev) => ({
                          ...prev,
                          [assignment.id]: { ...submissionDraft, submissionNote: event.target.value },
                        }))
                      }
                      rows={3}
                      placeholder="제출 메모를 입력하세요."
                      className="mt-3 w-full rounded-xl border border-white/10 bg-[#0f1420] px-4 py-3 text-white outline-none"
                    />
                    <button
                      type="button"
                      onClick={() => handleSubmitAssignment(assignment.id)}
                      disabled={submitting || !submissionDraft.submissionUrl.trim()}
                      className="mt-3 inline-flex items-center gap-2 rounded-xl bg-blue-500/20 px-4 py-2.5 text-sm font-medium text-blue-300 transition-colors hover:bg-blue-500/30 disabled:opacity-50"
                    >
                      <Send size={14} />
                      제출하기
                    </button>
                  </div>
                ) : null}

                {isMentor && assignment.submission && assignment.status === 'SUBMITTED' ? (
                  <div className="mt-4 rounded-2xl border border-white/5 bg-[#131a2b] p-4">
                    <p className="mb-3 text-sm font-medium text-white">피드백 작성</p>
                    <textarea
                      value={feedbackDraft.feedbackContent}
                      onChange={(event) =>
                        setFeedbackDrafts((prev) => ({
                          ...prev,
                          [assignment.id]: { ...feedbackDraft, feedbackContent: event.target.value },
                        }))
                      }
                      rows={4}
                      placeholder="멘티에게 전달할 피드백을 적어주세요."
                      className="w-full rounded-xl border border-white/10 bg-[#0f1420] px-4 py-3 text-white outline-none"
                    />
                    <input
                      type="text"
                      value={feedbackDraft.grade}
                      onChange={(event) =>
                        setFeedbackDrafts((prev) => ({
                          ...prev,
                          [assignment.id]: { ...feedbackDraft, grade: event.target.value },
                        }))
                      }
                      placeholder="평가 또는 한 줄 총평"
                      className="mt-3 w-full rounded-xl border border-white/10 bg-[#0f1420] px-4 py-3 text-white outline-none"
                    />
                    <button
                      type="button"
                      onClick={() => handleFeedback(assignment.id)}
                      disabled={submitting || !feedbackDraft.feedbackContent.trim()}
                      className="mt-3 inline-flex items-center gap-2 rounded-xl bg-emerald-500/20 px-4 py-2.5 text-sm font-medium text-emerald-300 transition-colors hover:bg-emerald-500/30 disabled:opacity-50"
                    >
                      저장하기
                    </button>
                  </div>
                ) : null}
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
}
