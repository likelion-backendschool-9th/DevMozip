'use client';
import styles from './page.module.css';
import { useCallback, useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useCurrentLocation as useLocation } from '@/lib/navigation';
import { useAuth } from '@/lib/state/authStore';
import { browserStorage, FeedRequestError, feedPostId, readFeedPost } from '@/lib/social/feedApi';
import { Button } from '@/components/common/Button';
import { FeedPostCard } from '@/app/feeds/_components/FeedPostCard';
import { Comments } from '@/app/feeds/[postId]/_components/Comments';
import type { FeedPost } from '@/types/feedApi';

type State = { key: string; post: FeedPost | null; error: { status: number; message: string } | null };
const scope = (name: string) => [styles.scope, name].join(' ');

/** 8.5 게시글 상세. 처음 읽는 글이면 조회수 집계를 요청한다(localStorage devmozip:feed-views:v1). */
export default function PostDetailPage() {
    const { postId: raw = '' } = useParams<{ postId: string }>();
    const postId = feedPostId(raw);
    const location = useLocation();
    const { user } = useAuth();
    const [attempt, setAttempt] = useState(0);
    // 로그인 상태가 바뀌면 요청자 기준 값(좋아요·내 글 여부)을 다시 받는다.
    const key = `${postId}:${user?.email ?? ''}:${attempt}`;
    const [state, setState] = useState<State>({ key: '', post: null, error: null });

    useEffect(() => {
        if (!postId) return;
        const controller = new AbortController();
        void readFeedPost(postId, browserStorage(), controller.signal).then(post => {
            if (!controller.signal.aborted) setState({ key, post, error: null });
        }).catch(error => {
            if (controller.signal.aborted) return;
            setState({ key, post: null, error: error instanceof FeedRequestError
                ? { status: error.status, message: error.message } : { status: 502, message: '게시글을 불러오지 못했어요.' } });
        });
        return () => controller.abort();
    }, [postId, key]);

    useEffect(() => {
        if (location.hash !== '#comments' || !state.post) return;
        const id = requestAnimationFrame(() => document.getElementById('comments')?.scrollIntoView({ block: 'start' }));
        return () => cancelAnimationFrame(id);
    }, [location.hash, postId, state.post]);

    const updatePost = useCallback((next: FeedPost) => setState(current => ({ ...current, post: next })), []);
    const updateCommentCount = useCallback((count: number) => setState(current => current.post && current.post.commentCount !== count
        ? { ...current, post: { ...current.post, commentCount: count } } : current), []);

    const notFound = !postId || state.key === key && state.error?.status === 404;
    if (notFound) return <div className={scope('container planned')}>
        <section className={scope('planned-card')}>
            <h1>게시글을 찾을 수 없어요.</h1>
            <p>삭제·숨김 처리된 게시글입니다.</p>
            <Link className={scope('button')} href="/feeds">전체 피드로</Link>
        </section>
    </div>;
    if (state.key !== key) return <div className={scope('container planned')}>
        <section className={scope('planned-card')} role="status"><h1>게시글을 불러오는 중이에요.</h1></section>
    </div>;
    if (!state.post) return <div className={scope('container planned')}>
        <section className={scope('planned-card')} role="alert">
            <h1>게시글을 불러오지 못했어요.</h1>
            <p>{state.error?.message}</p>
            <Button onClick={() => setAttempt(value => value + 1)}>다시 시도</Button>
        </section>
    </div>;
    return <div className={scope('container sns-detail-page')}>
        <Link className={scope('text-link')} href="/feeds">← 전체 피드</Link>
        <FeedPostCard post={state.post} full onChange={updatePost} />
        <Comments postId={state.post.id} onCountChange={updateCommentCount} />
    </div>;
}
