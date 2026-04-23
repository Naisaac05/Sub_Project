'use client';

interface Props {
  page: number;        // 0-indexed
  totalPages: number;
  onPageChange: (page: number) => void;
}

export function Pagination({ page, totalPages, onPageChange }: Props) {
  if (totalPages <= 1) return null;
  const prev = () => onPageChange(Math.max(0, page - 1));
  const next = () => onPageChange(Math.min(totalPages - 1, page + 1));
  return (
    <div className="flex items-center justify-center gap-3 py-4">
      <button
        onClick={prev}
        disabled={page === 0}
        className="rounded-md border border-slate-300 px-3 py-1 text-sm text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        ← 이전
      </button>
      <span className="text-sm text-slate-700 font-medium">
        {page + 1} / {totalPages}
      </span>
      <button
        onClick={next}
        disabled={page >= totalPages - 1}
        className="rounded-md border border-slate-300 px-3 py-1 text-sm text-slate-700 hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed"
      >
        다음 →
      </button>
    </div>
  );
}
