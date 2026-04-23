"use client";

import Link from "next/link";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AdminStatusBadge } from "@/components/admin/AdminStatusBadge";
import type { AdminPaymentListItem, PaymentStatus } from "../_types";

function formatKRW(n: number) {
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW" }).format(n);
}

const STATUS_CLASSNAMES: Record<PaymentStatus, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  CONFIRMED: "bg-emerald-100 text-emerald-800",
  CANCELLED: "bg-red-100 text-red-800",
  FAILED: "bg-zinc-100 text-zinc-700",
};

const STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: "대기",
  CONFIRMED: "확정",
  CANCELLED: "취소",
  FAILED: "실패",
};

export function PaymentListTable({ rows }: { rows: AdminPaymentListItem[] }) {
  if (!rows.length) return <div className="py-12 text-center text-muted-foreground">조건에 맞는 결제가 없습니다</div>;
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>주문ID</TableHead>
          <TableHead>사용자</TableHead>
          <TableHead className="text-right">금액</TableHead>
          <TableHead>상태</TableHead>
          <TableHead>결제일</TableHead>
          <TableHead className="text-right">액션</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((p) => (
          <TableRow key={p.id}>
            <TableCell className="font-mono text-xs">{p.orderId}</TableCell>
            <TableCell>
              {p.userName}
              <div className="text-xs text-muted-foreground">{p.userEmail}</div>
            </TableCell>
            <TableCell className="text-right">{formatKRW(p.amount)}</TableCell>
            <TableCell>
              <AdminStatusBadge
                label={STATUS_LABELS[p.status]}
                className={STATUS_CLASSNAMES[p.status]}
              />
            </TableCell>
            <TableCell className="text-sm text-muted-foreground">{new Date(p.createdAt).toLocaleString("ko-KR")}</TableCell>
            <TableCell className="text-right">
              <Link href={`/admin/payments/${p.id}`} className="text-sm text-primary underline-offset-4 hover:underline">
                상세 ›
              </Link>
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
