import LmsSidebar from '@/components/lms/LmsSidebar';
import MentorApprovedGate from '@/components/guards/MentorApprovedGate';

export const dynamic = 'force-dynamic';

export default function LmsLayout({ children }: { children: React.ReactNode }) {
  return (
    <MentorApprovedGate>
      <div className="flex min-h-screen bg-[#0a0e1a]">
        <LmsSidebar />
        <main className="flex-1 p-8 overflow-auto">
          {children}
        </main>
      </div>
    </MentorApprovedGate>
  );
}
