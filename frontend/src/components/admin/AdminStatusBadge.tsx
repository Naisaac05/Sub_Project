type Props = {
  label: string;
  className?: string; // e.g. "bg-emerald-100 text-emerald-800"
};

export function AdminStatusBadge({ label, className = 'bg-zinc-100 text-zinc-700' }: Props) {
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}
