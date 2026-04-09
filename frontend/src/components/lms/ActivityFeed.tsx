interface Activity {
  type: string;
  title: string;
  createdAt: string;
}

interface ActivityFeedProps {
  activities: Activity[];
}

const typeLabels: Record<string, string> = {
  ASSIGNMENT: '과제',
  NOTE: '노트',
  SESSION: '세션',
  ASSIGNMENT_FEEDBACK: '피드백',
};

export default function ActivityFeed({ activities }: ActivityFeedProps) {
  if (activities.length === 0) {
    return <p className="text-gray-500 text-sm">최근 활동이 없습니다</p>;
  }

  return (
    <div className="space-y-3">
      {activities.map((activity, i) => (
        <div key={i} className="flex items-center gap-3 py-2">
          <div className="w-2 h-2 rounded-full bg-blue-400 shrink-0" />
          <div className="flex-1 min-w-0">
            <p className="text-white text-sm truncate">{activity.title}</p>
            <p className="text-gray-500 text-xs">
              {typeLabels[activity.type] || activity.type} · {new Date(activity.createdAt).toLocaleDateString('ko-KR')}
            </p>
          </div>
        </div>
      ))}
    </div>
  );
}
