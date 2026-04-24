"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { format } from "date-fns";
import { ArrowLeft } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";

import { getAdminPost, type AdminPostDetail, type AdminPostCommentItem } from "@/lib/admin/posts";
import { PostDeleteDialog } from "../_components/PostDeleteDialog";
import { CommentDeleteDialog } from "../_components/CommentDeleteDialog";

export default function AdminPostDetailPage() {
  const params = useParams();
  const id = Number(params.id);

  const [detail, setDetail] = useState<AdminPostDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [postDelOpen, setPostDelOpen] = useState(false);
  const [commentDelTarget, setCommentDelTarget] = useState<number | null>(null);

  useEffect(() => {
    if (!id) return;
    let ignore = false;
    setLoading(true);
    getAdminPost(id)
      .then((d) => { if (!ignore) setDetail(d); })
      .catch((e: unknown) => {
        if (ignore) return;
        const msg = (e as { response?: { data?: { message?: string } } })
          ?.response?.data?.message ?? "상세를 불러오지 못했습니다";
        setError(msg);
      })
      .finally(() => { if (!ignore) setLoading(false); });
    return () => { ignore = true; };
  }, [id]);

  if (loading) return <div className="space-y-4"><Skeleton className="h-8 w-48" /><Skeleton className="h-64 w-full" /></div>;
  if (error)   return <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>;
  if (!detail) return null;

  const onCommentSoftDeleted = (updated: AdminPostCommentItem) => {
    setDetail((d) => d ? {
      ...d,
      commentCount: Math.max(0, d.commentCount - 1),
      comments: d.comments.map((c) => c.id === updated.id ? updated : c),
    } : d);
  };

  return (
    <div className="space-y-6 pb-24">
      <Link href="/admin/posts" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" /> 목록
      </Link>

      <div className="flex flex-wrap items-center gap-2">
        <h1 className="text-2xl font-semibold">{detail.title}</h1>
        <Badge variant="outline">{detail.category}</Badge>
        {detail.deleted && <Badge variant="destructive">🗑 삭제됨</Badge>}
      </div>

      {detail.deleted && detail.deletionReason && (
        <Alert variant="destructive">
          <AlertDescription>
            <strong>삭제 사유:</strong> {detail.deletionReason}
            {detail.deletedAt && (
              <> · {format(new Date(detail.deletedAt), "yyyy-MM-dd HH:mm")}</>
            )}
          </AlertDescription>
        </Alert>
      )}

      <Card>
        <CardHeader><CardTitle className="text-base">본문</CardTitle></CardHeader>
        <CardContent className="whitespace-pre-wrap text-sm leading-6">
          {detail.content}
        </CardContent>
      </Card>

      <Card>
        <CardHeader><CardTitle className="text-base">작성자</CardTitle></CardHeader>
        <CardContent className="flex items-center justify-between">
          <div className="space-y-1">
            <div>{detail.authorName}</div>
            <div className="text-sm text-muted-foreground">
              {detail.authorEmail} · {detail.authorRole}
            </div>
          </div>
          {detail.authorId && (
            <Link href={`/admin/users/${detail.authorId}`}>
              <Button variant="outline" size="sm">회원 상세 ›</Button>
            </Link>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">댓글 ({detail.commentCount})</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {detail.comments.length === 0 && (
            <div className="text-sm text-muted-foreground">댓글이 없습니다.</div>
          )}
          {detail.comments.map((c) => (
            <div key={c.id}
                 className={`rounded-md border p-3 ${c.deleted ? "opacity-60 bg-muted" : ""}`}>
              <div className="flex items-center justify-between text-sm">
                <div className="flex items-center gap-2">
                  <span className="font-medium">{c.authorName}</span>
                  <span className="text-muted-foreground">
                    {format(new Date(c.createdAt), "yyyy-MM-dd HH:mm")}
                  </span>
                  {c.deleted && <Badge variant="destructive">삭제됨</Badge>}
                </div>
                {!c.deleted && (
                  <Button variant="outline" size="sm" onClick={() => setCommentDelTarget(c.id)}>
                    삭제
                  </Button>
                )}
              </div>
              <div className={`mt-2 text-sm ${c.deleted ? "line-through" : ""}`}>
                {c.deleted ? "관리자에 의해 삭제됨" : c.content}
              </div>
              {c.deleted && c.deletionReason && (
                <div className="mt-1 text-xs text-muted-foreground">사유: {c.deletionReason}</div>
              )}
            </div>
          ))}
        </CardContent>
      </Card>

      {!detail.deleted && (
        <div className="fixed bottom-0 left-0 right-0 border-t bg-background p-4 flex justify-end">
          <Button variant="destructive" onClick={() => setPostDelOpen(true)}>
            게시물 삭제
          </Button>
        </div>
      )}

      <PostDeleteDialog
        open={postDelOpen}
        onOpenChange={setPostDelOpen}
        postId={detail.id}
        postTitle={detail.title}
        onSuccess={(next) => setDetail(next)}
      />
      {commentDelTarget !== null && (
        <CommentDeleteDialog
          open={commentDelTarget !== null}
          onOpenChange={(v) => { if (!v) setCommentDelTarget(null); }}
          postId={detail.id}
          commentId={commentDelTarget}
          onSuccess={onCommentSoftDeleted}
        />
      )}
    </div>
  );
}
