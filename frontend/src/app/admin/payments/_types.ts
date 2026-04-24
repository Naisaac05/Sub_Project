export type PaymentStatus = "PENDING" | "CONFIRMED" | "CANCELLED" | "FAILED";

export interface AdminPaymentListItem {
  id: number;
  orderId: string;
  userId: number;
  userName: string;
  userEmail: string;
  amount: number;
  status: PaymentStatus;
  createdAt: string;
  cancelledAt: string | null;
  matchingId: number | null;
}

export interface AdminPaymentSummary {
  totalAmount: number;
  confirmedCount: number;
  refundedAmount: number;
  refundRate: number;
}

export interface AdminPaymentDetail {
  id: number;
  orderId: string;
  paymentKey: string | null;
  amount: number;
  discountApplied: number;
  installmentMonths: number;
  courseType: string;
  monthsBundled: number;
  renewalCount: number;
  status: PaymentStatus;
  createdAt: string;
  cancelledAt: string | null;
  cancelReason: string | null;
  user: { id: number; name: string; email: string; role: string } | null;
  application: { id: number; category: string } | null;
  matching: { id: number; mentorName: string; status: string } | null;
  refund: {
    processedByAdminId: number;
    processedByAdminName: string | null;
    cancelledAt: string;
    reason: string;
  } | null;
}
