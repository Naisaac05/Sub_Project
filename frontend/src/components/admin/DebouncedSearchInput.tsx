'use client';

import { useEffect, useState } from 'react';

interface Props {
  value: string;
  onChange: (next: string) => void;
  placeholder?: string;
  delay?: number;
}

export function DebouncedSearchInput({ value, onChange, placeholder, delay = 300 }: Props) {
  const [internal, setInternal] = useState(value);

  useEffect(() => {
    setInternal(value);
  }, [value]);

  useEffect(() => {
    if (internal === value) return;
    const t = setTimeout(() => onChange(internal), delay);
    return () => clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [internal, delay]);

  return (
    <input
      type="text"
      value={internal}
      onChange={(e) => setInternal(e.target.value)}
      placeholder={placeholder}
      className="rounded-md border border-slate-300 px-3 py-1.5 text-sm w-72 focus:outline-none focus:ring-2 focus:ring-slate-900"
    />
  );
}
