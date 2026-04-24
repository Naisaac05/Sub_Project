"use client";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { AdminPaymentSummary } from "../_types";

function formatKRW(n: number): string {
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW" }).format(n);
}

export function PaymentSummaryCards({ summary }: { summary: AdminPaymentSummary | undefined }) {
  const s = summary ?? { totalAmount: 0, confirmedCount: 0, refundedAmount: 0, refundRate: 0 };
  return (
    <div className="grid grid-cols-4 gap-4">
      <SummaryCard title="총 결제액" value={formatKRW(s.totalAmount)} />
      <SummaryCard title="확정 건수" value={`${s.confirmedCount}건`} />
      <SummaryCard title="환불액" value={formatKRW(s.refundedAmount)} emphasis="danger" />
      <SummaryCard title="환불률" value={`${(s.refundRate * 100).toFixed(1)}%`} />
    </div>
  );
}

function SummaryCard({ title, value, emphasis }: { title: string; value: string; emphasis?: "danger" }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm text-muted-foreground">{title}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className={emphasis === "danger" ? "text-2xl font-semibold text-red-600" : "text-2xl font-semibold"}>{value}</div>
      </CardContent>
    </Card>
  );
}
