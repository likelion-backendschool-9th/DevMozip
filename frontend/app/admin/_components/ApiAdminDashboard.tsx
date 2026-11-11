'use client';
import { AdminLoadView, cls } from './AdminParts';
import { adminTime } from '@/lib/administration/adminApi';
import { dashboardStats, parseAdminDashboard } from '@/lib/administration/adminDashboard';
import { useAdminResource, type AdminLoad } from '@/lib/administration/useAdminResource';
import type { AdminDashboard } from '@/types/adminApi';

/** Mock 토글 OFF의 관리자 대시보드 (WBS 14.1). */
export function ApiAdminDashboard() {
    const { load, reload } = useAdminResource('/dashboard', parseAdminDashboard);
    return (
        <section className={cls('governance-panel admin-main')} aria-label="대시보드 관리">
            <h2>오늘의 DevMozip</h2>
            <DashboardView load={load} onRetry={reload} />
        </section>
    );
}

export function DashboardView({ load, onRetry }: { load: AdminLoad<AdminDashboard>; onRetry: () => void }) {
    return (
        <AdminLoadView load={load} onRetry={onRetry} loadingText="운영 현황을 불러오고 있어요.">
            {data => (
                <>
                    <div className={cls('admin-stats')}>
                        {dashboardStats(data).map(([label, value]) => (
                            <article key={label}>
                                <span>{label}</span>
                                <strong>{value}</strong>
                            </article>
                        ))}
                    </div>
                    <h3>최근 운영 변경</h3>
                    <p>회원 제재와 신고 처리 기록입니다. 게시글·공고 숨김과 삭제, 키워드 변경은 기록하지 않습니다.</p>
                    {!data.recentActions.length && <p>아직 변경 이력이 없습니다.</p>}
                    {data.recentActions.map((action, index) => (
                        <p key={`${action.type}-${action.createdAt}-${index}`}>
                            {adminTime(action.createdAt)} · {action.actorNickname ?? '-'} · {action.summary}
                        </p>
                    ))}
                </>
            )}
        </AdminLoadView>
    );
}
