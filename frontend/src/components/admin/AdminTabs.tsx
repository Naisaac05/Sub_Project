import type { ReactNode } from 'react';

type TabItem<V extends string> = { value: V; label: string };

type Props<V extends string> = {
  items: TabItem<V>[];
  value: V;
  onChange: (next: V) => void;
  ariaLabel?: string;
  /**
   * 'primary'  — 큰 탭 (기본). rounded-md px-3 py-1.5 text-sm
   *              active: bg-slate-900 text-white
   *              inactive: bg-slate-100 text-slate-700 hover:bg-slate-200
   * 'secondary' — 작은 탭. rounded-md border px-3 py-1 text-xs
   *              active: border-slate-700 bg-slate-700 text-white
   *              inactive: border-slate-300 bg-white text-slate-600 hover:bg-slate-50
   */
  variant?: 'primary' | 'secondary';
  /** 탭 버튼 우측에 추가할 노드 (검색 입력 등). 지정하면 flex 행 안에 함께 배치됩니다. */
  trailing?: ReactNode;
};

export function AdminTabs<V extends string>({
  items,
  value,
  onChange,
  ariaLabel,
  variant = 'primary',
  trailing,
}: Props<V>) {
  return (
    <div className="flex flex-wrap items-center gap-2" role="group" aria-label={ariaLabel}>
      {items.map((t) => {
        const isActive = t.value === value;
        let cls: string;
        if (variant === 'secondary') {
          cls =
            'rounded-md border px-3 py-1 text-xs transition-colors ' +
            (isActive
              ? 'border-slate-700 bg-slate-700 text-white'
              : 'border-slate-300 bg-white text-slate-600 hover:bg-slate-50');
        } else {
          cls =
            'rounded-md px-3 py-1.5 text-sm transition-colors ' +
            (isActive
              ? 'bg-slate-900 text-white'
              : 'bg-slate-100 text-slate-700 hover:bg-slate-200');
        }
        return (
          <button key={t.value} aria-pressed={isActive} onClick={() => onChange(t.value)} className={cls}>
            {t.label}
          </button>
        );
      })}
      {trailing && <div className="ml-auto">{trailing}</div>}
    </div>
  );
}
