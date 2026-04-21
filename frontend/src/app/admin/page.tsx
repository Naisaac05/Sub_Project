import { redirect } from 'next/navigation';

/**
 * /admin 진입 시 멘토 심사로 리다이렉트.
 * 추후 대시보드 페이지가 생기면 이 파일을 대시보드 콘텐츠로 대체.
 */
export default function AdminIndex() {
  redirect('/admin/mentor');
}
