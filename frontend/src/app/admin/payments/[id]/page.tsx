"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { AdminStatusBadge } from "@/components/admin/AdminStatusBadge";

import { getPaymentDetail } from "../_api/adminPaymentApi";
import type { AdminPaymentDetail } from "../_types";
import { PaymentDetailSection } from "../_components/PaymentDetailSection";
import { PaymentRefundDialog } from "../_components/PaymentRefundDialog";
import {
  STATUS_CLASSNAMES,
  STATUS_LABELS,
  formatKRW,
} from "../_components/paymentStatus";

export default function AdminPaymentDetailPage({ params }: { params: { id: string } }) {
  const id = Number(params.id);

  const [detail, setDetail] = useState<AdminPaymentDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refundOpen, setRefundOpen] = useState(false);

  useEffect(() => {
    if (Number.isNaN(id)) {
      setLoading(false);
      return;
    }
    let ignore = false;
    setLoading(true);
    setError(null);
    getPaymentDetail(id)
      .then((d) => {
        if (!ignore) setDetail(d);
      })
      .catch((e: unknown) => {
        if (ignore) return;
        const err = e as { response?: { data?: { message?: string } } };
        setError(err?.response?.data?.message ?? String(e));
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [id]);

  if (Number.isNaN(id)) {
    return (
      <div className="space-y-4">
        <Link
          href="/admin/payments"
          className="text-sm text-primary underline-offset-4 hover:underline"
        >
          ← 목록
        </Link>
        <div className="rounded-md border border-red-300 bg-red-50 p-4 text-sm text-red-700">
          잘못된 결제 ID 입니다.
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Link
        href="/admin/payments"
        className="text-sm text-primary underline-offset-4 hover:underline"
      >
        ← 목록
      </Link>

      {loading && (
        <div className="text-sm text-slate-500 py-6 text-center">불러오는 중…</div>
      )}

      {error && (
        <div className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-700">
          에러: {error}
        </div>
      )}

      {detail && !loading && (
        <>
          <div className="flex items-center justify-between gap-4">
            <div className="space-y-1">
              <div className="flex items-center gap-3">
                <h1 className="font-mono text-2xl font-semibold">{detail.orderId}</h1>
                <AdminStatusBadge
                  label={STATUS_LABELS[detail.status]}
                  className={STATUS_CLASSNAMES[detail.status]}
                />
              </div>
              <p className="text-sm text-muted-foreground">
                결제일 {new Date(detail.createdAt).toLocaleString("ko-KR")} ·{" "}
                금액 {formatKRW(detail.amount)}
              </p>
            </div>

            {detail.status === "CANCELLED" && (
              <div className="flex items-center gap-2 rounded-lg border-2 border-red-300 bg-red-50 px-4 py-3 text-red-700 shadow-sm">
                <span className="text-xl" aria-hidden>🔒</span>
                <span className="text-base font-bold">재환불 불가</span>
              </div>
            )}
            {detail.status === "PENDING" && (
              <div className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-2 text-sm font-medium text-amber-700">
                결제 대기 상태입니다
              </div>
            )}
            {detail.status === "FAILED" && (
              <div className="rounded-lg border border-slate-300 bg-slate-50 px-4 py-2 text-sm font-medium text-slate-700">
                결제 실패 — 액션 불가
              </div>
            )}
          </div>

          <PaymentDetailSection detail={detail} />

          {detail.status === "CONFIRMED" && (
            <div className="sticky bottom-0 bg-background border-t pt-4 flex justify-end">
              <Button variant="destructive" onClick={() => setRefundOpen(true)}>
                환불 처리
              </Button>
            </div>
          )}

          <PaymentRefundDialog
            open={refundOpen}
            onOpenChange={setRefundOpen}
            detail={detail}
            onSuccess={(next) => setDetail(next)}
          />
        </>
      )}
    </div>
  );
}
