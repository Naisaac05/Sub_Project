import type { ReactNode } from 'react';
import LmsSidebar from '@/components/lms/LmsSidebar';

export const dynamic = 'force-dynamic';

export default function LmsLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen bg-[#0a0e1a]">
      <LmsSidebar />
      <main className="flex-1 overflow-auto p-8">{children}</main>
    </div>
  );
}
