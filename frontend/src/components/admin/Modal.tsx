'use client';

import { type ReactNode, useEffect } from 'react';

interface Props {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  width?: number;
}

export function Modal({ open, onClose, title, children, footer, width = 480 }: Props) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-lg shadow-xl"
        style={{ width }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="border-b border-slate-100 px-5 py-3">
          <h3 className="text-base font-semibold text-slate-900">{title}</h3>
        </div>
        <div className="px-5 py-4 space-y-3">{children}</div>
        {footer && <div className="border-t border-slate-100 px-5 py-3 flex justify-end gap-2">{footer}</div>}
      </div>
    </div>
  );
}

export function CancelButton({ onClick, label = '취소' }: { onClick: () => void; label?: string }) {
  return (
    <button
      onClick={onClick}
      className="rounded-md border border-slate-300 bg-slate-100 px-4 py-1.5 text-sm font-semibold text-slate-900 hover:bg-slate-200"
    >
      {label}
    </button>
  );
}

export function PrimaryButton({ onClick, disabled, children, variant = 'default' }: {
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
  variant?: 'default' | 'destructive' | 'warning';
}) {
  const cls =
    variant === 'destructive' ? 'bg-red-600 hover:bg-red-700' :
    variant === 'warning' ? 'bg-amber-600 hover:bg-amber-700' :
    'bg-slate-900 hover:bg-slate-800';
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`rounded-md px-4 py-1.5 text-sm font-semibold text-white disabled:opacity-50 disabled:cursor-not-allowed ${cls}`}
    >
      {children}
    </button>
  );
}

export function AmberAlert({ lines }: { lines: string[] }) {
  return (
    <div className="rounded-md bg-amber-50 border border-amber-200 px-3 py-2 space-y-1">
      {lines.map((line, i) => (
        <p key={i} className="text-xs text-amber-800">⚠ {line}</p>
      ))}
    </div>
  );
}
