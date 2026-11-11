'use client';
import Link from 'next/link';
import { Heart } from 'lucide-react';
import { Button } from '@/components/common/Button';
import { ProjectSummaryCard } from '@/components/projects/ProjectSummaryCard';
import { ApiError } from '@/app/projects/_components/api/ApiCommon';
import { useProjectLikes } from '@/lib/projects/projectLikeStore';
import styles from '../page.module.css';

export function LikedProjectsResult() {
    const likes = useProjectLikes();
    if (likes.error) return <ApiError error={likes.error} retry={likes.reload} />;
    if (!likes.ready) return <p role="status">좋아요한 프로젝트를 불러오는 중…</p>;
    if (!likes.items.length) return <div className={[styles.scope, 'empty-state'].join(' ')}>
        <h2>아직 좋아요한 프로젝트가 없어요.</h2>
        <p>프로젝트에서 마음에 드는 공고에 좋아요를 눌러보세요.</p>
        <Link href="/projects" className="button secondary">프로젝트 둘러보기</Link>
    </div>;
    return <>
        <p className="text-muted" role="status">좋아요한 프로젝트 {likes.items.length}개</p>
        <div className={styles.projectGrid} aria-busy={likes.loading}>
            {likes.items.map((project, index) => <section key={project.id} className={styles.projectItem}>
                <ProjectSummaryCard project={project} index={index} />
                <Button variant="secondary" disabled={likes.pending || likes.loading} aria-label={`${project.title} 좋아요 취소`} onClick={() => likes.toggle(project.id)}><Heart size={18} fill="currentColor" />좋아요 취소</Button>
            </section>)}
        </div>
    </>;
}
