import type { LucideIcon } from 'lucide-react';

interface StatCardProps {
  label: string;
  value: string | number;
  icon: LucideIcon;
  color?: string;
}

export default function StatCard({ label, value, icon: Icon, color = 'blue' }: StatCardProps) {
  const colorMap: Record<string, string> = {
    blue: 'from-blue-500 to-cyan-400',
    green: 'from-green-500 to-emerald-400',
    purple: 'from-purple-500 to-violet-400',
    orange: 'from-orange-500 to-amber-400',
  };

  return (
    <div className="bg-[#0f1420] border border-white/5 rounded-2xl p-6">
      <div className="flex items-center justify-between mb-4">
        <span className="text-gray-400 text-sm">{label}</span>
        <div className={`w-10 h-10 rounded-xl bg-gradient-to-br ${colorMap[color] || colorMap.blue} flex items-center justify-center`}>
          <Icon size={20} className="text-white" />
        </div>
      </div>
      <p className="text-white text-3xl font-bold">{value}</p>
    </div>
  );
}
