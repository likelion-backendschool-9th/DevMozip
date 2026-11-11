'use client';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { ApiAdminUsers } from './_components/ApiAdminUsers';
import { MockAdminUsers } from './_components/MockAdminUsers';

// WBS 14.2·14.3. Mock 토글 ON은 기존 Mock 화면, OFF는 서버 회원 관리 (docs/admin-plan.md 6.2).
export default function AdminUsersPage() {
    const { enabled } = useMockVisibility();
    return enabled ? <MockAdminUsers /> : <ApiAdminUsers />;
}
