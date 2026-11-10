'use client';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { ApiAdminAi } from './_components/ApiAdminAi';
import { MockAdminAi } from './_components/MockAdminAi';

// WBS 14.10·14.11. Mock 토글 ON은 기존 Mock 화면, OFF는 서버 AI 사용량·실패 기록 (docs/admin-plan.md 6.2).
export default function AdminAiPage() {
    const { enabled } = useMockVisibility();
    return enabled ? <MockAdminAi /> : <ApiAdminAi />;
}
