import type { PaymentStatus } from "../_types";

export const STATUS_CLASSNAMES: Record<PaymentStatus, string> = {
  PENDING: "bg-amber-100 text-amber-800",
  CONFIRMED: "bg-emerald-100 text-emerald-800",
  CANCELLED: "bg-red-100 text-red-800",
  FAILED: "bg-zinc-100 text-zinc-700",
};

export const STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: "대기",
  CONFIRMED: "확정",
  CANCELLED: "취소",
  FAILED: "실패",
};

export function formatKRW(n: number): string {
  return new Intl.NumberFormat("ko-KR", { style: "currency", currency: "KRW" }).format(n);
}
