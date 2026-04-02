import type { Metadata } from 'next';
import './globals.css';
import Providers from './Providers';

export const metadata: Metadata = {
  title: 'DevMatch - 실력 기반 멘토 매칭 플랫폼',
  description: '실력 테스트로 나의 수준을 파악하고, 딱 맞는 현직 개발자 멘토를 만나보세요. DevMatch에서 커리어 성장을 시작하세요.',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      <body className="antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
