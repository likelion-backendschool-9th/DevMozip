'use client';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { ApiAdminReports } from './_components/ApiAdminReports';
import { MockAdminReports } from './_components/MockAdminReports';

// WBS 14.8. Mock 토글 ON은 기존 Mock 화면, OFF는 서버 신고 내역 처리 (docs/admin-plan.md 6.2, D5).
export default function AdminReportsPage() {
    const { enabled } = useMockVisibility();
    return enabled ? <MockAdminReports /> : <ApiAdminReports />;
}
