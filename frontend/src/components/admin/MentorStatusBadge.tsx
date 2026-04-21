import { Badge } from '@/components/ui/badge';
import type { AdminMentorStatus } from '@/lib/admin-mentor';

const STATUS_CONFIG: Record<
  AdminMentorStatus,
  { label: string; className: string }
> = {
  PENDING: {
    label: '대기',
    className:
      'border-amber-200 bg-amber-100 text-amber-800 hover:bg-amber-100',
  },
  APPROVED: {
    label: '승인됨',
    className:
      'border-emerald-200 bg-emerald-100 text-emerald-800 hover:bg-emerald-100',
  },
  REJECTED: {
    label: '반려됨',
    className: 'border-red-200 bg-red-100 text-red-800 hover:bg-red-100',
  },
};

export default function MentorStatusBadge({
  status,
}: {
  status: AdminMentorStatus;
}) {
  const { label, className } = STATUS_CONFIG[status];
  return <Badge className={className}>{label}</Badge>;
}

export const MENTEE_LEVEL_LABEL: Record<string, string> = {
  BEGINNER: '주니어',
  INTERMEDIATE: '미들',
  ADVANCED: '시니어',
  ANY: '무관',
};
