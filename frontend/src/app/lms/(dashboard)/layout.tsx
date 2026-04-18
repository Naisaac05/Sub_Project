import LmsSidebar from '@/components/lms/LmsSidebar';

export const dynamic = 'force-dynamic';

export default function LmsLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen bg-[#0a0e1a]">
      <LmsSidebar />
      <main className="flex-1 p-8 overflow-auto">
        {children}
      </main>
    </div>
  );
}
