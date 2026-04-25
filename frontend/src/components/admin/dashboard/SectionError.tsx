'use client';

interface Props {
  message?: string;
  onRetry: () => void;
}

export function SectionError({ message = '데이터를 불러오지 못했습니다.', onRetry }: Props) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-lg border border-red-200 bg-red-50 p-6 text-sm text-red-700">
      <p>{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="rounded-md border border-red-300 bg-white px-3 py-1.5 text-xs font-medium text-red-700 hover:bg-red-100"
      >
        재시도
      </button>
    </div>
  );
}
