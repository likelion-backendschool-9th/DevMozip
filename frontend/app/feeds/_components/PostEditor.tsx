'use client';
import styles from './PostEditor.module.css';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useAuth } from '@/lib/state/authStore';
import { FeedRequestError, feedPostId, fetchFeedPost } from '@/lib/social/feedApi';
import { Button } from '@/components/common/Button';
import { PostForm } from '@/app/feeds/_components/PostForm';
import type { FeedPost } from '@/types/feedApi';

type State = { key: string; post: FeedPost | null; status: number; message: string };
const scope = (name: string) => [styles.scope, name].join(' ');

function Notice({ title, body, href = '/feeds', label = '피드로', retry }: { title: string; body?: string; href?: string; label?: string; retry?: () => void }) {
    return <div className={scope('container planned')}>
        <section className={scope('planned-card')}>
            <h1>{title}</h1>
            {body && <p>{body}</p>}
            {retry ? <Button onClick={retry}>다시 시도</Button> : <Link className={scope('button')} href={href}>{label}</Link>}
        </section>
    </div>;
}

/** 8.4 작성(/feeds/new)·8.6 수정(/feeds/{id}/edit). 수정 화면은 조회수를 올리지 않도록 view 없이 불러온다. */
export function PostEditorPage() {
    const { postId: raw } = useParams<{ postId?: string }>();
    const editing = raw !== undefined;
    const postId = editing ? feedPostId(raw) : null;
    const { user } = useAuth();
    const [attempt, setAttempt] = useState(0);
    const key = `${postId}:${user?.email ?? ''}:${attempt}`;
    const [state, setState] = useState<State>({ key: '', post: null, status: 0, message: '' });
    const signedIn = !!user;

    useEffect(() => {
        if (!postId || !signedIn) return;
        const controller = new AbortController();
        void fetchFeedPost(postId, false, controller.signal).then(post => {
            if (!controller.signal.aborted) setState({ key, post, status: 200, message: '' });
        }).catch(error => {
            if (!controller.signal.aborted) setState({ key, post: null, status: error instanceof FeedRequestError ? error.status : 502,
                message: error instanceof Error ? error.message : '게시글을 불러오지 못했어요.' });
        });
        return () => controller.abort();
    }, [postId, key, signedIn]);

    if (!user) return <Notice title="로그인하고 이야기를 나눠보세요." href="/login" label="로그인" />;
    if (editing) {
        if (!postId || state.key === key && state.status === 404) return <Notice title="게시글을 찾을 수 없어요." body="삭제되었거나 숨김 처리된 게시글입니다." />;
        if (state.key !== key) return <Notice title="게시글을 불러오는 중이에요." body="잠시만 기다려 주세요." />;
        if (!state.post) return <Notice title="게시글을 불러오지 못했어요." body={state.message} retry={() => setAttempt(value => value + 1)} />;
        if (!state.post.mine) return <Notice title="본인 게시글만 수정할 수 있습니다." />;
        if (state.post.type === 'REPOST') return <Notice title="퍼간 글의 의견은 원본 글에서 수정해요."
            body="원본 글의 ‘퍼가기 관리’에서 의견을 고치거나 퍼가기를 취소할 수 있어요."
            href={state.post.original?.available ? `/feeds/${state.post.original.id}` : '/feeds'} label={state.post.original?.available ? '원본 글로' : '피드로'} />;
    }
    const post = editing ? state.post ?? undefined : undefined;
    return <div className={scope('container sns-editor-page')}>
        <Link className={scope('text-link')} href="/profile/me?tab=posts">← 내 포스팅</Link>
        <section>
            <div className={scope('eyebrow')}>SHARE YOUR STORY</div>
            <h1>{post ? '이야기 수정하기' : '새로운 이야기 나누기'}</h1>
            <p>작은 경험이 누군가의 다음 시도에 도움이 될 수 있어요.</p>
            <PostForm key={post ? post.id : 'new'} post={post} />
        </section>
    </div>;
}
