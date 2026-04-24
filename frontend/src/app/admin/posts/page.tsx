"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import type { DateRange } from "react-day-picker";
import { format } from "date-fns";

import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from "@/components/ui/select";
import { Checkbox } from "@/components/ui/checkbox";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";

import { AdminListHeader } from "@/components/admin/AdminListHeader";
import { DebouncedSearchInput } from "@/components/admin/DebouncedSearchInput";
import { AdminDateRangePicker } from "@/components/admin/AdminDateRangePicker";
import { Pagination } from "@/components/admin/Pagination";

import {
  listAdminPosts, listAdminPostCategories,
  type AdminPostListItem, type PageResponse,
} from "@/lib/admin/posts";

const DEFAULT_SIZE = 20;
const ALL_CATEGORY = "__ALL__";

export default function AdminPostsPage() {
  const router = useRouter();
  const search = useSearchParams();

  const page = Number(search.get("page") ?? "0");
  const category = search.get("category") ?? "";
  const q = search.get("q") ?? "";
  const fromStr = search.get("from") ?? "";
  const toStr = search.get("to") ?? "";
  const includeDeleted = (search.get("includeDeleted") ?? "true") === "true";

  const [data, setData] = useState<PageResponse<AdminPostListItem> | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const dateRange = useMemo<DateRange | undefined>(() => {
    if (!fromStr && !toStr) return undefined;
    return {
      from: fromStr ? new Date(fromStr) : undefined,
      to: toStr ? new Date(toStr) : undefined,
    };
  }, [fromStr, toStr]);

  const updateParam = useCallback((patch: Record<string, string | undefined>) => {
    const next = new URLSearchParams(search.toString());
    Object.entries(patch).forEach(([k, v]) => {
      if (v === undefined || v === "") next.delete(k);
      else next.set(k, v);
    });
    if (!("page" in patch)) next.set("page", "0");
    router.replace(`/admin/posts?${next.toString()}`);
  }, [search, router]);

  useEffect(() => {
    listAdminPostCategories().then(setCategories).catch(() => setCategories([]));
  }, []);

  useEffect(() => {
    setLoading(true);
    setError(null);
    listAdminPosts({
      page, size: DEFAULT_SIZE,
      category: category || undefined,
      q: q || undefined,
      from: fromStr || undefined,
      to: toStr || undefined,
      includeDeleted,
    })
      .then(setData)
      .catch((e: unknown) => {
        const msg = (e as { response?: { data?: { message?: string } } })
          ?.response?.data?.message ?? "목록을 불러오지 못했습니다";
        setError(msg);
      })
      .finally(() => setLoading(false));
  }, [page, category, q, fromStr, toStr, includeDeleted]);

  return (
    <div className="space-y-4">
      <AdminListHeader title="게시물 관리" description="커뮤니티 게시물을 조회·강제 삭제합니다." />

      <div className="flex flex-wrap items-center gap-2">
        <Select
          value={category || ALL_CATEGORY}
          onValueChange={(v) => updateParam({ category: v === ALL_CATEGORY ? undefined : v })}
        >
          <SelectTrigger className="w-40"><SelectValue placeholder="카테고리" /></SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_CATEGORY}>전체 카테고리</SelectItem>
            {categories.map((c) => (
              <SelectItem key={c} value={c}>{c}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <AdminDateRangePicker
          value={dateRange}
          onChange={(r) => updateParam({
            from: r?.from ? format(r.from, "yyyy-MM-dd") : undefined,
            to:   r?.to   ? format(r.to,   "yyyy-MM-dd") : undefined,
          })}
        />

        <DebouncedSearchInput
          value={q}
          onChange={(v) => updateParam({ q: v || undefined })}
          placeholder="제목/내용/작성자 검색"
        />

        <label className="flex items-center gap-2 text-sm text-muted-foreground ml-2">
          <Checkbox
            checked={includeDeleted}
            onCheckedChange={(v) => updateParam({ includeDeleted: v ? "true" : "false" })}
          />
          삭제된 글 포함
        </label>
      </div>

      {error && (
        <Alert variant="destructive">
          <AlertDescription className="flex items-center justify-between">
            <span>{error}</span>
            <Button variant="outline" size="sm" onClick={() => updateParam({})}>재시도</Button>
          </AlertDescription>
        </Alert>
      )}

      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>제목</TableHead>
            <TableHead>카테고리</TableHead>
            <TableHead>작성자</TableHead>
            <TableHead className="text-right">👍</TableHead>
            <TableHead className="text-right">💬</TableHead>
            <TableHead className="text-right">👀</TableHead>
            <TableHead>작성일</TableHead>
            <TableHead>상태</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {loading && Array.from({ length: 5 }).map((_, i) => (
            <TableRow key={`sk-${i}`}>
              {Array.from({ length: 8 }).map((_, j) => (
                <TableCell key={j}><Skeleton className="h-4 w-full" /></TableCell>
              ))}
            </TableRow>
          ))}
          {!loading && data?.content.length === 0 && (
            <TableRow>
              <TableCell colSpan={8} className="h-24 text-center text-muted-foreground">
                조건에 맞는 게시물이 없습니다.
              </TableCell>
            </TableRow>
          )}
          {!loading && data?.content.map((p) => (
            <TableRow key={p.id} className={p.deleted ? "text-slate-400" : ""}>
              <TableCell className={p.deleted ? "line-through" : ""}>
                <Link href={`/admin/posts/${p.id}`} className="hover:underline">{p.title}</Link>
              </TableCell>
              <TableCell><Badge variant="outline">{p.category}</Badge></TableCell>
              <TableCell>{p.authorName}</TableCell>
              <TableCell className="text-right">{p.likeCount}</TableCell>
              <TableCell className="text-right">{p.commentCount}</TableCell>
              <TableCell className="text-right">{p.viewCount}</TableCell>
              <TableCell>{format(new Date(p.createdAt), "yyyy-MM-dd HH:mm")}</TableCell>
              <TableCell>
                {p.deleted
                  ? <Badge variant="destructive">삭제됨</Badge>
                  : <Badge>정상</Badge>}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {data && (
        <Pagination
          page={data.number}
          totalPages={data.totalPages}
          onPageChange={(p) => updateParam({ page: String(p) })}
        />
      )}
    </div>
  );
}
