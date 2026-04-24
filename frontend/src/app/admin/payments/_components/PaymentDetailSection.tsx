"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { AdminPaymentDetail } from "../_types";
import { formatKRW } from "./paymentStatus";

function formatInstallment(months: number): string {
  return months === 0 ? "일시불" : `${months}개월`;
}

function formatRenewal(count: number): string {
  return `${count} (${count === 0 ? "최초" : "연장"})`;
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("ko-KR");
}

function DlRow({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <>
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="text-foreground">{children}</dd>
    </>
  );
}

export function PaymentDetailSection({ detail }: { detail: AdminPaymentDetail }) {
  return (
    <div className="space-y-4">
      {/* 주문 정보 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">주문 정보</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid grid-cols-2 gap-y-2 text-sm">
            <DlRow label="주문 ID">
              <span className="font-mono">{detail.orderId}</span>
            </DlRow>
            <DlRow label="PaymentKey">
              <span className="font-mono text-xs break-all">{detail.paymentKey ?? "-"}</span>
            </DlRow>
            <DlRow label="결제 금액">{formatKRW(detail.amount)}</DlRow>
            <DlRow label="할인 적용">{formatKRW(detail.discountApplied)}</DlRow>
            <DlRow label="할부">{formatInstallment(detail.installmentMonths)}</DlRow>
            <DlRow label="코스 유형">{detail.courseType}</DlRow>
            <DlRow label="묶음 개월">{detail.monthsBundled}개월</DlRow>
            <DlRow label="갱신 횟수">{formatRenewal(detail.renewalCount)}</DlRow>
          </dl>
        </CardContent>
      </Card>

      {/* 사용자 */}
      {detail.user && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">사용자</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-y-2 text-sm">
              <DlRow label="ID">{detail.user.id}</DlRow>
              <DlRow label="이름">{detail.user.name}</DlRow>
              <DlRow label="이메일">{detail.user.email}</DlRow>
              <DlRow label="권한">{detail.user.role}</DlRow>
            </dl>
          </CardContent>
        </Card>
      )}

      {/* 연결된 매칭 */}
      {detail.matching && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">연결된 매칭</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <dl className="grid grid-cols-2 gap-y-2 text-sm">
              <DlRow label="매칭 ID">#{detail.matching.id}</DlRow>
              <DlRow label="멘토">{detail.matching.mentorName}</DlRow>
              <DlRow label="매칭 상태">{detail.matching.status}</DlRow>
            </dl>
            <p className="text-xs text-muted-foreground">
              환불 시 매칭이 함께 취소되며, 멘티의 LMS 접근이 차단됩니다.
            </p>
          </CardContent>
        </Card>
      )}

      {/* 환불 이력 */}
      {detail.refund && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">환불 이력</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-2 gap-y-2 text-sm">
              <DlRow label="환불 처리자">
                {detail.refund.processedByAdminName ?? `#${detail.refund.processedByAdminId}`}
              </DlRow>
              <DlRow label="환불 일시">{formatDateTime(detail.refund.cancelledAt)}</DlRow>
              <DlRow label="환불 사유">
                <span className="whitespace-pre-wrap">{detail.refund.reason}</span>
              </DlRow>
            </dl>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
