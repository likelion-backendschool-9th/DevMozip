'use client';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { ApiAdminDashboard } from './_components/ApiAdminDashboard';
import { MockAdminDashboard } from './_components/MockAdminDashboard';

// WBS 14.1. Mock 토글 ON은 기존 Mock 대시보드, OFF는 서버 집계 (docs/admin-plan.md 6.2).
export default function AdminDashboardPage() {
    const { enabled } = useMockVisibility();
    return enabled ? <MockAdminDashboard /> : <ApiAdminDashboard />;
}
