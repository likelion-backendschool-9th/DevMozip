'use client';
import { adminStyles as styles } from './_components/AdminShell';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { useProjects } from '@/lib/state/projectStore';

export default function AdminDashboardPage() {
    const { publicUsers, administration } = useAuth();
    const { state: profiles } = useProfiles();
    const { state: recruitment, projects } = useProjects();
    const subscribed = publicUsers.filter(u => u.role === 'premium');
    const suspended = Object.values(administration.members).filter(m => m.status === 'suspended').length;
    const stats = [
        ['회원', publicUsers.length],
        ['프리미엄', subscribed.length],
        ['프로젝트', projects.length],
        ['게시글', profiles.posts.filter(p => !p.hidden).length],
        [
            '미처리 프로젝트 신고',
            recruitment.reports.filter(r => !administration.resolutions[`project:${r.id}`]).length,
        ],
        ['정지 회원', suspended],
    ];

    return (
        <section className={[styles.scope, 'governance-panel admin-main'].join(' ')} aria-label="대시보드 관리">
            <h2>오늘의 DevMozip</h2>
            <div className={[styles.scope, 'admin-stats'].join(' ')}>
                {stats.map(([label, value]) => (
                    <article key={label}>
                        <span>{label}</span>
                        <strong>{value}</strong>
                    </article>
                ))}
            </div>
            <h3>최근 구독·운영 변경</h3>
            {!administration.audit.length && <p>아직 변경 이력이 없습니다.</p>}
            {administration.audit.slice(0, 12).map(a => (
                <p key={a.id}>
                    {new Date(a.createdAt).toLocaleString('ko-KR')} ·{' '}
                    {publicUsers.find(u => u.id === a.actorId)?.nickname} · {a.action}
                </p>
            ))}
        </section>
    );
}
