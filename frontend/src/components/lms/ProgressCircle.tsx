interface ProgressCircleProps {
  value: number;
  label: string;
  size?: number;
}

export default function ProgressCircle({ value, label, size = 120 }: ProgressCircleProps) {
  const radius = (size - 12) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (value / 100) * circumference;

  return (
    <div className="flex flex-col items-center gap-2">
      <svg width={size} height={size} className="-rotate-90">
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none"
          stroke="rgba(255,255,255,0.05)" strokeWidth="10" />
        <circle cx={size / 2} cy={size / 2} r={radius} fill="none"
          stroke="url(#gradient)" strokeWidth="10" strokeLinecap="round"
          strokeDasharray={circumference} strokeDashoffset={offset}
          className="transition-all duration-700" />
        <defs>
          <linearGradient id="gradient" x1="0%" y1="0%" x2="100%" y2="0%">
            <stop offset="0%" stopColor="#3b82f6" />
            <stop offset="100%" stopColor="#22d3ee" />
          </linearGradient>
        </defs>
      </svg>
      <div className="absolute flex flex-col items-center">
        <span className="text-white text-2xl font-bold">{value}%</span>
      </div>
      <span className="text-gray-400 text-sm">{label}</span>
    </div>
  );
}
