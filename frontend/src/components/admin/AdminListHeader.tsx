import type { ReactNode } from 'react';

type Props = {
  title: string;
  /** h1 아래 설명 문구 */
  description?: string;
  /** 헤더 우측 액션 슬롯 (버튼 등). 지정하면 flex justify-between 레이아웃이 적용됩니다. */
  actions?: ReactNode;
  /** h1 위에 렌더링할 요소 (뒤로 가기 버튼 등). */
  preTitle?: ReactNode;
  /** 제목 우측 또는 아래에 인라인으로 추가할 요소 (배지·이메일 등). */
  subtitle?: ReactNode;
};

export function AdminListHeader({ title, description, actions, preTitle, subtitle }: Props) {
  const leftBlock = (
    <div>
      {preTitle}
      <h1 className="text-2xl font-bold text-slate-900">{title}</h1>
      {subtitle}
      {description && <p className="text-sm text-slate-600">{description}</p>}
    </div>
  );

  if (actions) {
    return (
      <header className="flex items-start justify-between">
        {leftBlock}
        <div>{actions}</div>
      </header>
    );
  }

  return <header>{leftBlock}</header>;
}
