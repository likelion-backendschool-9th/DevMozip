'use client';
import styles from './Comments.module.css';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '@/lib/state/authStore';
import { usePrototype } from '@/lib/state/prototypeStore';
import {
    createFeedComment, deleteFeedComment, FEED_COMMENT_MAX, feedAuthorHref, feedAuthorName, fetchFeedComments, updateFeedComment,
} from '@/lib/social/feedApi';
import { Button } from '@/components/common/Button';
import { Textarea } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { FeedReportDialog } from '@/app/feeds/_components/FeedReportDialog';
import type { FeedComment } from '@/types/feedApi';

const scope = (name: string) => [styles.scope, name].join(' ');
const message = (error: unknown, fallback: string) => error instanceof Error ? error.message : fallback;
const validComment = (text: string) => text.trim().length > 0 && text.length <= FEED_COMMENT_MAX;

/**
 * 댓글 목록·작성·수정·삭제·신고 (8.9~8.12, 8.16). 목록은 공개이고 쓰기는 로그인이 필요하다.
 * onCountChange는 다시 불러오는 조건에 들어가므로 상세 화면이 바뀌지 않는 함수(useCallback)로 넘긴다.
 */
export function Comments({ postId, onCountChange }: { postId: number; onCountChange: (count: number) => void }) {
    const { user } = useAuth();
    const { notify } = usePrototype();
    const email = user?.email ?? '';
    const [attempt, setAttempt] = useState(0);
    const loadKey = `${postId}:${email}:${attempt}`;
    const [state, setState] = useState<{ key: string; comments: FeedComment[] | null; error: string }>({ key: '', comments: null, error: '' });
    const [body, setBody] = useState('');
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const [edit, setEdit] = useState<{ id: number; body: string } | null>(null);
    const [remove, setRemove] = useState<number | null>(null);
    const [report, setReport] = useState<number | null>(null);

    useEffect(() => {
        const controller = new AbortController();
        void fetchFeedComments(postId, controller.signal).then(comments => {
            if (controller.signal.aborted) return;
            setState({ key: loadKey, comments, error: '' });
            onCountChange(comments.length);
        }).catch(failure => {
            if (!controller.signal.aborted) setState({ key: loadKey, comments: null, error: message(failure, '댓글을 불러오지 못했어요.') });
        });
        return () => controller.abort();
    }, [postId, loadKey, onCountChange]);

    const loading = state.key !== loadKey;
    const comments = loading ? null : state.comments;
    const replace = (next: FeedComment[]) => {
        setState({ key: loadKey, comments: next, error: '' });
        onCountChange(next.length);
    };

    async function submit() {
        if (busy || !comments) return;
        if (!validComment(body)) { setError('댓글은 1~1,000자로 입력해 주세요.'); return; }
        setBusy(true);
        try {
            const created = await createFeedComment(postId, body);
            replace([...comments, created]);
            setBody('');
            setError('');
        } catch (failure) { setError(message(failure, '댓글을 등록하지 못했어요.')); }
        finally { setBusy(false); }
    }

    async function saveEdit() {
        if (busy || !edit || !comments) return;
        if (!validComment(edit.body)) { notify('댓글은 1~1,000자로 입력해 주세요.'); return; }
        setBusy(true);
        try {
            const updated = await updateFeedComment(edit.id, edit.body);
            replace(comments.map(item => item.id === updated.id ? updated : item));
            setEdit(null);
        } catch (failure) { notify(message(failure, '댓글을 수정하지 못했어요.')); }
        finally { setBusy(false); }
    }

    async function confirmRemove() {
        if (busy || remove === null || !comments) return;
        setBusy(true);
        try {
            await deleteFeedComment(remove);
            replace(comments.filter(item => item.id !== remove));
            setRemove(null);
        } catch (failure) { notify(message(failure, '댓글을 삭제하지 못했어요.')); }
        finally { setBusy(false); }
    }

    return <section className={scope('sns-comments')} id="comments">
        <h2>댓글 <span>{comments ? comments.length : '—'}</span></h2>
        {user ? <form onSubmit={event => { event.preventDefault(); void submit(); }}>
            <label className={scope('sr-only')} htmlFor="new-comment">댓글 입력</label>
            <Textarea id="new-comment" value={body} maxLength={FEED_COMMENT_MAX} rows={3} disabled={busy || !comments}
                onChange={event => { setBody(event.target.value); setError(''); }} placeholder="존중하는 대화로 경험을 나눠주세요." />
            {error && <p role="alert" className={scope('form-error')}>{error}</p>}
            <Button type="submit" disabled={busy || !comments}>{busy ? '등록하는 중…' : '댓글 등록'}</Button>
        </form> : <p className={scope('auth-info')}><Link href="/login">로그인하고 댓글을 남겨보세요 →</Link></p>}
        {loading && <div className={scope('empty-state')} role="status">댓글을 불러오는 중이에요.</div>}
        {!loading && state.error && <div className={scope('empty-state')} role="alert">
            <p>{state.error}</p>
            <Button variant="secondary" onClick={() => setAttempt(value => value + 1)}>다시 시도</Button>
        </div>}
        {comments && !comments.length && <div className={scope('empty-state')}>첫 번째 의견을 남겨주세요.</div>}
        <div className={scope('comment-list')}>{(comments ?? []).map(comment => {
            const href = feedAuthorHref(comment.author);
            const name = feedAuthorName(comment.author);
            const avatar = <ProfileAvatar name={name} color="lavender" small image={comment.author.profileImage ?? undefined} />;
            return <article className={scope('sns-comment')} key={comment.id}>
                {href ? <Link href={href}>{avatar}</Link> : avatar}
                <div>
                    <header>
                        {href ? <Link href={href}><strong>{name}</strong></Link> : <strong>{name}</strong>}
                        <time dateTime={comment.createdAt}>
                            {new Date(comment.createdAt).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' })}{comment.edited ? ' · 수정됨' : ''}
                        </time>
                    </header>
                    {edit?.id === comment.id ? <form onSubmit={event => { event.preventDefault(); void saveEdit(); }}>
                        <Textarea aria-label="댓글 수정" maxLength={FEED_COMMENT_MAX} rows={3} value={edit.body} disabled={busy}
                            onChange={event => setEdit({ ...edit, body: event.target.value })} />
                        <div className={scope('inline-actions')}>
                            <Button type="button" variant="secondary" disabled={busy} onClick={() => setEdit(null)}>취소</Button>
                            <Button type="submit" disabled={busy}>수정 저장</Button>
                        </div>
                    </form> : <p className={scope('preserve-text')}>{comment.content}</p>}
                    <div className={scope('inline-actions')}>
                        {comment.mine ? <>
                            <button disabled={busy} onClick={() => setEdit({ id: comment.id, body: comment.content })}>수정</button>
                            <button disabled={busy} onClick={() => setRemove(comment.id)}>삭제</button>
                        </> : user
                            ? <button disabled={comment.reported} onClick={() => setReport(comment.id)}>{comment.reported ? '신고됨' : '댓글 신고'}</button>
                            : <Link href="/login">로그인 후 신고</Link>}
                    </div>
                </div>
            </article>;
        })}</div>
        {remove !== null && <Modal title="댓글을 삭제할까요?" onClose={() => setRemove(null)}>
            <div className={scope('modal-actions')}>
                <Button variant="secondary" disabled={busy} onClick={() => setRemove(null)}>취소</Button>
                <Button disabled={busy} onClick={() => void confirmRemove()}>삭제</Button>
            </div>
        </Modal>}
        {report !== null && <FeedReportDialog targetType="comment" targetId={report} onClose={() => setReport(null)}
            onReported={() => comments && replace(comments.map(item => item.id === report ? { ...item, reported: true } : item))} />}
    </section>;
}
