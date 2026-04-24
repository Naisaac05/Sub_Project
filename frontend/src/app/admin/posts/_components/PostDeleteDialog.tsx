"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter,
  DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { deleteAdminPost, type AdminPostDetail } from "@/lib/admin/posts";

const schema = z.object({
  reason: z.string().min(10, "사유는 10자 이상").max(500, "사유는 500자 이하"),
});
type FormValues = z.infer<typeof schema>;

interface Props {
  open: boolean;
  onOpenChange: (next: boolean) => void;
  postId: number;
  postTitle: string;
  onSuccess: (next: AdminPostDetail) => void;
}

export function PostDeleteDialog({ open, onOpenChange, postId, postTitle, onSuccess }: Props) {
  const [submitting, setSubmitting] = useState(false);
  const { register, handleSubmit, watch, reset, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { reason: "" },
  });
  const len = watch("reason")?.length ?? 0;

  useEffect(() => {
    if (!open) {
      reset({ reason: "" });
      setSubmitting(false);
    }
  }, [open, reset]);

  async function onSubmit(v: FormValues) {
    setSubmitting(true);
    try {
      const next = await deleteAdminPost(postId, v.reason);
      toast.success("게시물이 삭제되었습니다");
      onSuccess(next);
      reset();
      onOpenChange(false);
    } catch (e) {
      const msg = (e as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? "삭제에 실패했습니다";
      toast.error(msg);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>게시물 삭제</DialogTitle>
          <DialogDescription>
            &ldquo;{postTitle}&rdquo; 게시물을 소프트 삭제합니다.
          </DialogDescription>
        </DialogHeader>

        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div>
            <Textarea
              rows={4}
              placeholder="삭제 사유를 10~500자로 입력하세요"
              disabled={submitting}
              {...register("reason")}
            />
            <div className="mt-1 flex justify-between text-xs text-muted-foreground">
              <span className="text-destructive">{errors.reason?.message}</span>
              <span>{len}/500</span>
            </div>
          </div>

          <Alert variant="destructive">
            <AlertDescription>
              <ul className="list-disc pl-4 space-y-1">
                <li>게시물은 소프트 삭제되며 사용자측에서는 숨겨집니다.</li>
                <li>삭제 사유는 감사 로그에 기록됩니다.</li>
                <li>이미 삭제된 게시물은 재삭제할 수 없습니다.</li>
              </ul>
            </AlertDescription>
          </Alert>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={submitting}>
              취소
            </Button>
            <Button type="submit" variant="destructive" disabled={submitting}>
              {submitting ? "처리 중…" : "삭제 확정"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
