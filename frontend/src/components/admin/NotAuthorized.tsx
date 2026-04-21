'use client';

import Link from 'next/link';
import { Lock } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

export default function NotAuthorized() {
  return (
    <div className="flex min-h-[60vh] items-center justify-center px-6">
      <Card className="w-full max-w-md">
        <CardContent className="flex flex-col items-center gap-5 py-10 text-center">
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-slate-100">
            <Lock className="h-8 w-8 text-slate-500" aria-hidden="true" />
          </div>

          <div className="space-y-1.5">
            <h1 className="text-2xl font-semibold tracking-tight text-slate-900">
              403 · 접근 권한 없음
            </h1>
            <p className="text-sm leading-relaxed text-slate-600">
              관리자 전용 페이지입니다.
              <br />
              계정 권한이 필요하다면 담당자에게 문의해 주세요.
            </p>
          </div>

          <Button asChild>
            <Link href="/">홈으로 돌아가기</Link>
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
