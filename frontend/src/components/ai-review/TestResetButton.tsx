// 🧪 테스트 전용: AI 리뷰 세션 초기화 버튼
//
// 환경변수 NEXT_PUBLIC_AI_REVIEW_TEST_RESET=true 일 때만 렌더.
//
// 제거 방법:
//   1. 이 파일 삭제
//   2. frontend/src/app/tests/results/[id]/review/page.tsx 에서 import/render 2줄 제거
//   3. frontend/src/lib/ai-review.ts 의 resetAiReviewSession 함수 제거
//   4. 백엔드 TestResetAiReviewController.java 등 제거 (해당 파일 주석 참고)
//   5. .env 에서 NEXT_PUBLIC_AI_REVIEW_TEST_RESET 제거
// 추가일: 2026-05-27
'use client';

import { useState } from 'react';
import { resetAiReviewSession } from '@/lib/ai-review';

interface TestResetButtonProps {
  testResultId: number;
}

export function TestResetButton({ testResultId }: TestResetButtonProps) {
  const [resetting, setResetting] = useState(false);

  // 환경변수 OFF 시 컴포넌트 자체가 아무것도 렌더하지 않음
  if (process.env.NEXT_PUBLIC_AI_REVIEW_TEST_RESET !== 'true') {
    return null;
  }

  const handleReset = async () => {
    const ok = window.confirm(
      'AI 리뷰 세션을 처음부터 다시 시작합니다.\n현재 대화 내역이 모두 삭제됩니다.\n진행할까요?'
    );
    if (!ok) {
      return;
    }
    setResetting(true);
    try {
      await resetAiReviewSession(testResultId);
      window.location.reload();
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      window.alert('초기화 실패: ' + msg);
      setResetting(false);
    }
  };

  return (
    <button
      type="button"
      onClick={handleReset}
      disabled={resetting}
      title="🧪 테스트 전용 — 운영 환경엔 표시되지 않습니다"
      className="rounded border border-red-400 bg-red-50 px-3 py-1 text-sm text-red-700 hover:bg-red-100 disabled:opacity-50"
    >
      {resetting ? '초기화 중...' : '🧪 세션 초기화 (테스트)'}
    </button>
  );
}
