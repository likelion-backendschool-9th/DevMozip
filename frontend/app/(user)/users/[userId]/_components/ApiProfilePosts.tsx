'use client';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { Card } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { fetchProfilePosts } from '@/lib/profiles/profilePostsApi';
import { likedPostCategoryLabel } from '@/lib/profiles/likedPostsApi';
import { useAuth } from '@/lib/state/authStore';
import type { ProfilePostsPage } from '@/types/profilePosts';
import styles from '@/components/posts/PostCard.module.css';
export type ProfilePostsState = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; value: ProfilePostsPage };
export function ProfilePostsResult({ result, onRetry, onPage }: { result: ProfilePostsState; onRetry: () => void; onPage: (page: number) => void }) {
    if (result.status === 'loading') return <p role="status">포스팅을 불러오는 중…</p>;
    if (result.status === 'error') return <div role="alert"><p>{result.message}</p><Button variant="secondary" onClick={onRetry}>다시 시도</Button></div>;
    const { items, page, totalElements, totalPages } = result.value;
    return <>
        <p className="text-muted">포스팅 {totalElements}개</p>
        {!items.length && <div className="empty-state">{totalElements === 0 ? '아직 표시할 포스팅이 없습니다.' : '이 페이지에는 포스팅이 없습니다.'}
            {totalElements > 0 && <Button variant="secondary" onClick={() => onPage(0)}>첫 페이지 보기</Button>}</div>}
        {items.map(post => <Card key={post.id} className={`${styles.scope} sns-post-card`}>
            <header className={`${styles.scope} sns-author`}>
                <strong>{post.author.nickname || '작성자'}</strong>
                <span>{likedPostCategoryLabel(post.category)}{post.type === 'REPOST' ? ' · 퍼온 글' : ''}</span>
                {post.createdAt && <time dateTime={post.createdAt}>{new Date(post.createdAt).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}</time>}
            </header>
            {post.title && <h3>{post.title}</h3>}
            <p className={`${styles.scope} preserve-text sns-post-body`}>{post.contentPreview || '내용 없음'}</p>
            {post.originalPost && <blockquote><strong>원본 작성자 · {post.originalPost.author.nickname || '작성자'}</strong>
                <p className="preserve-text">{post.originalPost.contentPreview || '내용 없음'}</p>
                <Link href={`/feeds/${post.originalPost.id}`}>원본 보기</Link></blockquote>}
            <Link href={`/feeds/${post.id}`}>전체 글 보기 →</Link>
        </Card>)}
        {totalPages > 0 && <nav aria-label="포스팅 페이지">
            <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>이전</Button>
            <span> {page + 1} / {totalPages} </span>
            <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>다음</Button>
        </nav>}
    </>;
}
function PostsLoader({ target, page, onPage, onRetry }: { target: string; page: number; onPage: (page: number) => void; onRetry: () => void }) {
    const [result, setResult] = useState<ProfilePostsState>({ status: 'loading' });
    useEffect(() => {
        const controller = new AbortController();
        void fetchProfilePosts(target, page, controller.signal).then(value => {
            if (!controller.signal.aborted) setResult({ status: 'ready', value });
        }).catch(error => {
            if (!controller.signal.aborted) setResult({ status: 'error', message: error instanceof Error ? error.message : '포스팅을 불러오지 못했어요.' });
        });
        return () => controller.abort();
    }, [target, page]);
    return <ProfilePostsResult result={result} onRetry={onRetry} onPage={onPage} />;
}
export function ApiProfilePosts({ slug, own }: { slug?: string; own: boolean }) {
    const { user } = useAuth();
    const [page, setPage] = useState(0), [attempt, setAttempt] = useState(0);
    const target = own ? 'me' : slug;
    return <div><div className="tab-section-title"><h2>생각과 경험의 기록</h2></div>
        {target ? <PostsLoader key={`${user?.email || ''}:${target}:${page}:${attempt}`} target={target} page={page} onPage={setPage} onRetry={() => setAttempt(n => n + 1)} />
            : <p role="alert">프로필 주소를 확인할 수 없어요.</p>}
    </div>;
}
