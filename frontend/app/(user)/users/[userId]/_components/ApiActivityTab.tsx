'use client';
import { useState } from 'react';
import Link from 'next/link';
import { ArrowUpRight } from 'lucide-react';
import { ActivityProjectCard } from './ActivityProjectCard';
import { projectDetailHref } from '@/lib/projects/projectNavigation';
import { Button } from '@/components/common/Button';
import { useAuth } from '@/lib/state/authStore';
import type { ProjectActivityPage } from '@/types/projectActivity';
import styles from './ActivityTab.module.css';
import { useProjectResource } from '@/lib/projects/useProjectApi';
import { statusLabels } from '@/lib/projects/projectApiContract';
import { projectRoleLabel } from '@/lib/projects/projectChoices';

export function ApiActivityTab({ slug, own }: { slug?: string; own: boolean }) {
    const { user } = useAuth();
    const target = own ? 'me' : slug;
    const [page, setPage] = useState(0);
    return <div>
        <div className={`${styles.scope} ${styles.heading} tab-section-title`}><div><h2>함께 만들어온 프로젝트</h2><p>DevMozip에서 모집하거나 참여한 프로젝트 이력만 표시합니다.</p></div>{own && <Link className={`button secondary ${styles.manageLink}`} href="/my-projects">내 프로젝트 관리<ArrowUpRight size={18} aria-hidden="true" /></Link>}</div>
        {target ? <ActivityList key={`${user?.email || ''}:${target}:${page}`} target={target} page={page} onPage={setPage} /> : <p role="alert">프로필 태그를 확인할 수 없습니다.</p>}
    </div>;
}
function ActivityList({ target, page, onPage }: { target: string; page: number; onPage: (page: number) => void }) {
    const { data, error, reload } = useProjectResource<ProjectActivityPage>(`/activity/${encodeURIComponent(target)}?page=${page}&size=10`);
    if (error) return <div role="alert"><p>{error.message}</p><Button variant="secondary" onClick={reload}>다시 시도</Button></div>;
    if (!data) return <p role="status">프로젝트 이력을 불러오고 있습니다.</p>;
    return <>
        {data.items.length === 0 && <div className={`${styles.scope} empty-state`}>표시할 DevMozip 프로젝트 참여 기록이 없습니다.</div>}
        <div className={`${styles.scope} activity-list`}>{data.items.map(item => <ActivityProjectCard key={item.projectId}
            title={item.title} imageUrl={item.imageUrl} href={projectDetailHref(item.projectId,target==='me'?'/profile/me?tab=activity':`/users/${encodeURIComponent(target)}?tab=activity`)}
            status={statusLabels[item.status]} start={item.developmentStart} end={item.developmentEnd} completedAt={item.completedAt}
            role={(item.role==='HOST'?'주최자':item.jobRole?projectRoleLabel(item.jobRole):'참여자')+(item.membershipStatus==='KICKED'?' · 강퇴됨':item.membershipStatus==='LEFT'?' · 참여 종료':'')}
            mark={item.title.trim().charAt(0)} color={['peach','lavender','mint','blue'][item.projectId%4]} />)}</div>
        {data.totalPages > 1 && <nav className={styles.pagination} aria-label="프로젝트 활동 페이지">
            <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>이전</Button>
            <span>{page + 1} / {data.totalPages}</span>
            <Button variant="secondary" disabled={page + 1 >= data.totalPages} onClick={() => onPage(page + 1)}>다음</Button>
        </nav>}
    </>;
}
