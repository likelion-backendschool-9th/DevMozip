'use client';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { ApiAdminPosts } from './_components/ApiAdminPosts';
import { MockAdminPosts } from './_components/MockAdminPosts';

// WBS 14.6·14.7. Mock 토글 ON은 기존 Mock 화면, OFF는 서버 게시글·댓글 관리 (docs/admin-plan.md 6.2).
// 서버 모드의 SNS 신고 처리는 신고 내역(/admin/reports)에서 한다.
export default function AdminPostsPage() {
    const { enabled } = useMockVisibility();
    return enabled ? <MockAdminPosts /> : <ApiAdminPosts />;
}
