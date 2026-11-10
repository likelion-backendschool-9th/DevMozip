'use client';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { ApiAdminSubscriptions } from './_components/ApiAdminSubscriptions';
import { MockAdminSubscriptions } from './_components/MockAdminSubscriptions';

// WBS 14.12·14.13. Mock 토글 ON은 기존 Mock 화면, OFF는 서버 구독·결제 조회 (docs/admin-plan.md 6.2).
export default function AdminSubscriptionsPage() {
    const { enabled } = useMockVisibility();
    return enabled ? <MockAdminSubscriptions /> : <ApiAdminSubscriptions />;
}
