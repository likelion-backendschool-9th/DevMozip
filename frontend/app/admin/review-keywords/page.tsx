'use client';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { ApiAdminReviewKeywords } from './_components/ApiAdminReviewKeywords';
import { MockAdminReviewKeywords } from './_components/MockAdminReviewKeywords';

// WBS 14.9. Mock 토글 ON은 기존 Mock 화면, OFF는 서버 리뷰 키워드 관리 (docs/admin-plan.md 6.2).
export default function AdminReviewKeywordsPage() {
    const { enabled } = useMockVisibility();
    return enabled ? <MockAdminReviewKeywords /> : <ApiAdminReviewKeywords />;
}
