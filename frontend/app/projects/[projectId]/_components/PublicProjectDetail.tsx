'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getAiPublicProject } from '@/lib/matching/aiApi';
import type { AiPublicProject } from '@/types/aiRecommendation';
import { Card } from '@/components/common/Card';
import styles from '../page.module.css';

export function PublicProjectDetail({ projectId }: { projectId: number }) {
    const [project, setProject] = useState<AiPublicProject>();
    const [failed, setFailed] = useState(false);
    useEffect(() => {
        const controller = new AbortController();
        void getAiPublicProject(projectId, controller.signal).then(value => {
            if (!controller.signal.aborted) setProject(value);
        }).catch(() => { if (!controller.signal.aborted) setFailed(true); });
        return () => controller.abort();
    }, [projectId]);
    return <main className={[styles.scope, 'container project-detail-page'].join(' ')}>
        <Link href="/ai/matching" className={[styles.scope, 'text-link'].join(' ')}>AI 추천으로</Link>
        {!project ? <Card className={[styles.scope, 'profile-section'].join(' ')}>
            <h1>{failed ? '프로젝트를 불러올 수 없어요.' : '프로젝트 정보를 불러오는 중…'}</h1>
            {failed && <p>삭제되었거나 공개되지 않은 프로젝트일 수 있습니다.</p>}
        </Card> : <>
            <header className={[styles.scope, 'project-detail-heading'].join(' ')}><h1>{project.title}</h1><p>{project.summary}</p></header>
            <Card className={[styles.scope, 'profile-section'].join(' ')}>
                <h2>프로젝트 소개</h2><p className={[styles.scope, 'preserve-text'].join(' ')}>{project.description || '추가 소개가 없습니다.'}</p>
                <h2 className={[styles.scope, 'sub-heading'].join(' ')}>기술 스택</h2>
                <div className={[styles.scope, 'profile-tags'].join(' ')}>{project.stacks.map(stack => <span key={stack}>{stack}</span>)}</div>
                <h2 className={[styles.scope, 'sub-heading'].join(' ')}>모집 직군</h2><p>{project.roles.join(' · ') || '모집 직군 미등록'}</p>
            </Card>
        </>}
    </main>;
}
