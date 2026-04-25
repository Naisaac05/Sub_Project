import { redirect } from 'next/navigation';

/**
 * /admin 진입 시 대시보드로 리다이렉트.
 */
export default function AdminIndex() {
  redirect('/admin/dashboard');
}
