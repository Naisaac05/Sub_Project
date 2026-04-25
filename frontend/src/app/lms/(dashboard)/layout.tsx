import type { ReactNode } from 'react';
import MentorApprovedGate from '@/components/guards/MentorApprovedGate';

export const dynamic = 'force-dynamic';

export default function LmsDashboardLayout({ children }: { children: ReactNode }) {
  return <MentorApprovedGate>{children}</MentorApprovedGate>;
}
