"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Alert, AlertDescription } from "@/components/ui/alert";

import { refundPayment } from "../_api/adminPaymentApi";
import type { AdminPaymentDetail } from "../_types";
import { formatKRW } from "./paymentStatus";

const schema = z.object({
  reason: z
    .string()
    .min(10, "10자 이상 입력하세요")
    .max(500, "500자 이하로 입력하세요"),
});

type Values = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  detail: AdminPaymentDetail;
  onSuccess: (next: AdminPaymentDetail) => void;
}

export function PaymentRefundDialog({ open, onOpenChange, detail, onSuccess }: Props) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { reason: "" },
  });

  const reason = watch("reason") ?? "";

  // reset when dialog closes
  useEffect(() => {
    if (!open) {
      reset({ reason: "" });
      setServerError(null);
      setIsSubmitting(false);
    }
  }, [open, reset]);

  const onSubmit = async (v: Values) => {
    setServerError(null);
    setIsSubmitting(true);
    try {
      const next = await refundPayment(detail.id, v.reason);
      toast.success("환불 처리되었습니다");
      onSuccess(next);
      onOpenChange(false);
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } }; message?: string };
      const msg = err?.response?.data?.message ?? err?.message ?? "환불 처리에 실패했습니다";
      setServerError(msg);
      toast.error(msg);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>결제 환불</DialogTitle>
          <DialogDescription>
            <span className="font-mono">{detail.orderId}</span> 주문을 환불 처리합니다.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          {/* 환불 금액 */}
          <div className="rounded-md border bg-muted/30 px-3 py-2 text-sm">
            <div className="text-muted-foreground">환불 금액</div>
            <div className="text-base font-semibold">
              {formatKRW(detail.amount)}{" "}
              <span className="text-xs font-normal text-muted-foreground">
                (전액 환불 고정)
              </span>
            </div>
          </div>

          {/* 사유 */}
          <div className="space-y-1">
            <label htmlFor="refund-reason" className="text-sm font-medium">
              환불 사유 <span className="text-destructive">*</span>
            </label>
            <Textarea
              id="refund-reason"
              rows={4}
              placeholder="환불 사유를 10~500자로 입력하세요"
              disabled={isSubmitting}
              {...register("reason")}
            />
            <div className="flex items-center justify-between text-xs text-muted-foreground">
              <span className={errors.reason ? "text-destructive" : ""}>
                {errors.reason?.message ?? "\u00A0"}
              </span>
              <span
                className={
                  reason.length > 500 || (reason.length > 0 && reason.length < 10)
                    ? "text-destructive"
                    : ""
                }
              >
                {reason.length} / 500 (최소 10)
              </span>
            </div>
          </div>

          {/* Warning */}
          <Alert variant="destructive">
            <AlertDescription>
              <ul className="list-disc pl-4 space-y-1">
                <li>토스페이먼츠에 환불 요청이 전송됩니다 (취소 불가).</li>
                {detail.matching && (
                  <li>연결된 매칭이 함께 취소되어 멘티의 LMS 접근이 차단됩니다.</li>
                )}
                <li>본 작업은 감사 로그에 기록됩니다.</li>
              </ul>
            </AlertDescription>
          </Alert>

          {serverError && (
            <Alert variant="destructive">
              <AlertDescription>{serverError}</AlertDescription>
            </Alert>
          )}

          <DialogFooter>
            <Button
              type="button"
              variant="ghost"
              onClick={() => onOpenChange(false)}
              disabled={isSubmitting}
            >
              취소
            </Button>
            <Button type="submit" variant="destructive" disabled={isSubmitting}>
              {isSubmitting ? "처리 중…" : "환불 확정"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
