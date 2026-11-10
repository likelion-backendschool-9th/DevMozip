'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/state/authStore';
import { useQueryState } from '@/lib/navigation';
import { fetchLikedPosts, likedPostsPagination } from '@/lib/profiles/likedPostsApi';
import { ProfileSaveError } from '@/lib/profiles/profileUpdate';
import { LikedPostsResult, type LikedPostsResultState } from './_components/LikedPostsResult';
import { LikedProjectsResult } from './_components/LikedProjectsResult';
import styles from './page.module.css';
export default function LikedPostsPage() {
    const { user } = useAuth();
    const [params, setParams] = useQueryState();
    const tab = params.get('tab') === 'projects' ? 'projects' : 'posts';
    const pagination = likedPostsPagination(params);
    const page = pagination?.page, size = pagination?.size;
    const email = user?.email;
    const key = `${email || ''}:${page}:${size}`;
    const [result, setResult] = useState<{ key: string; state: LikedPostsResultState }>({ key, state: { status: 'loading' } });
    const [attempt, setAttempt] = useState(0);
    useEffect(() => {
        if (tab !== 'posts' || !email || page === undefined || size === undefined) return;
        const controller = new AbortController();
        setResult({ key, state: { status: 'loading' } });
        void fetchLikedPosts(page, size, controller.signal).then(value => {
            if (!controller.signal.aborted) setResult({ key, state: { status: 'ready', value } });
        }).catch(error => {
            if (!controller.signal.aborted) setResult({ key, state: { status: 'error', code: error instanceof ProfileSaveError ? error.status : 502, message: error instanceof Error ? error.message : '목록을 불러오지 못했어요.' } });
        });
        return () => controller.abort();
    }, [email, page, size, key, attempt, tab]);
    function select(nextPage: number) { const next = new URLSearchParams(params); next.set('page', String(nextPage)); if (!pagination) next.delete('size'); setParams(next); }
    function selectTab(tab: string) { const next = new URLSearchParams(params); next.set('tab', tab); next.delete('page'); next.delete('size'); setParams(next); }
    return <>
        <header className={[styles.scope, 'personal-page-heading'].join(' ')}><span className={[styles.scope, 'eyebrow'].join(' ')}>MY FAVORITES</span><h1>좋아요한 글</h1><p>좋아요한 포스트와 프로젝트를 모아 보세요.</p></header>
        <div className={[styles.scope, 'tabs'].join(' ')} role="tablist" aria-label="좋아요 유형">
            <button id="liked-posts-tab" role="tab" aria-selected={tab === 'posts'} aria-controls="liked-content" className={tab === 'posts' ? 'active' : ''} onClick={() => selectTab('posts')}>포스트</button>
            <button id="liked-projects-tab" role="tab" aria-selected={tab === 'projects'} aria-controls="liked-content" className={tab === 'projects' ? 'active' : ''} onClick={() => selectTab('projects')}>프로젝트</button>
        </div>
        <section id="liked-content" role="tabpanel" aria-labelledby={tab === 'posts' ? 'liked-posts-tab' : 'liked-projects-tab'}>
        {!user ? <Link href="/login?next=/profile/me/likes">로그인하고 내 목록을 확인해 주세요.</Link>
            : tab === 'projects' ? <LikedProjectsResult />
            : !pagination ? <div role="alert"><p>페이지와 조회 개수를 확인해 주세요.</p><button className="button secondary" onClick={() => select(0)}>첫 페이지 보기</button></div>
                : <LikedPostsResult result={result.key === key ? result.state : { status: 'loading' }} onRetry={() => setAttempt(v => v + 1)} onPage={select} />}
        </section>
    </>;
}
