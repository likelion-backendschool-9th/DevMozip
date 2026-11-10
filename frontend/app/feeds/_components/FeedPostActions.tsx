'use client';
import styles from '@/components/posts/PostActions.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { Bookmark, Flag, Heart, MessageCircle, Repeat2, Share2 } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { usePrototype } from '@/lib/state/prototypeStore';
import {
    cancelFeedRepost, changeFeedBookmark, FEED_OPINION_MAX, feedAuthorHref, feedAuthorName, repostFeed, toggleFeedLike,
    updateRepostOpinion,
} from '@/lib/social/feedApi';
import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { Input, Textarea } from '@/components/common/Input';
import type { FeedPost } from '@/types/feedApi';
import { FeedReportDialog } from './FeedReportDialog';

const scope = (name: string) => [styles.scope, name].join(' ');

/**
 * 게시글 반응 (8.7 좋아요, 8.8 북마크, 8.13 퍼가기, 8.14 외부 공유, 8.15 신고).
 * 요청 중에는 버튼을 막고, 화면은 서버가 돌려준 최종 상태로 맞춘다.
 */
export function FeedPostActions({ post, onChange }: { post: FeedPost; onChange: (next: FeedPost) => void }) {
    const { user } = useAuth();
    const { notify } = usePrototype();
    const [modal, setModal] = useState<'repost' | 'share' | 'report' | null>(null);
    const [pending, setPending] = useState<'like' | 'bookmark' | 'repost' | null>(null);
    const [opinion, setOpinion] = useState(post.myRepost?.content ?? '');
    const [repostError, setRepostError] = useState('');
    const url = typeof window === 'undefined' ? `/feeds/${post.id}` : new URL(`/feeds/${post.id}`, window.location.origin).href;
    const authorHref = feedAuthorHref(post.author);

    async function like() {
        if (pending) return;
        setPending('like');
        try {
            const result = await toggleFeedLike(post.id);
            onChange({ ...post, liked: result.liked, likeCount: result.likeCount });
        } catch (error) { notify(error instanceof Error ? error.message : '좋아요를 반영하지 못했어요.'); }
        finally { setPending(null); }
    }

    async function bookmark() {
        if (pending) return;
        setPending('bookmark');
        try {
            const result = await changeFeedBookmark(post.id, !post.bookmarked);
            onChange({ ...post, bookmarked: result.bookmarked });
            notify(result.bookmarked ? '북마크에 저장했어요.' : '북마크를 해제했어요.');
        } catch (error) { notify(error instanceof Error ? error.message : '북마크를 반영하지 못했어요.'); }
        finally { setPending(null); }
    }

    async function repost(text: string) {
        if (pending) return;
        if (text.length > FEED_OPINION_MAX) { setRepostError('퍼가기 의견은 1,000자 이내로 입력해 주세요.'); return; }
        setPending('repost');
        setRepostError('');
        try {
            if (post.myRepost) {
                const updated = await updateRepostOpinion(post.myRepost.id, text);
                onChange({ ...post, myRepost: { id: updated.id, content: updated.content } });
                notify('퍼간 글의 의견을 수정했어요.');
            } else {
                const created = await repostFeed(post.id, text);
                onChange({ ...post, myRepost: { id: created.id, content: created.content } });
                notify('내 프로필에 퍼갔어요.');
            }
            setModal(null);
        } catch (error) { setRepostError(error instanceof Error ? error.message : '퍼가지 못했어요.'); }
        finally { setPending(null); }
    }

    async function cancelRepost() {
        if (pending) return;
        setPending('repost');
        setRepostError('');
        try {
            await cancelFeedRepost(post.id);
            onChange({ ...post, myRepost: null });
            notify('퍼가기를 취소했어요.');
            setModal(null);
        } catch (error) { setRepostError(error instanceof Error ? error.message : '퍼가기를 취소하지 못했어요.'); }
        finally { setPending(null); }
    }

    return <>
        <div className={scope('sns-post-actions')}>
            {user ? <button aria-label={post.liked ? '좋아요 취소' : '좋아요'} aria-pressed={post.liked} disabled={pending === 'like'}
                className={scope(post.liked ? 'liked' : '')} onClick={() => void like()}>
                <Heart size={16} fill={post.liked ? 'currentColor' : 'none'} />{post.likeCount}
            </button> : <Link href="/login" aria-label="로그인하고 좋아요"><Heart size={16} />{post.likeCount}</Link>}
            <Link href={`/feeds/${post.id}#comments`} aria-label={`댓글 ${post.commentCount}개`}><MessageCircle size={16} />{post.commentCount}</Link>
            {user ? <button aria-label={post.bookmarked ? '북마크 취소' : '북마크'} aria-pressed={post.bookmarked} disabled={pending === 'bookmark'}
                onClick={() => void bookmark()}>
                <Bookmark size={16} fill={post.bookmarked ? 'currentColor' : 'none'} /><span>북마크</span>
            </button> : <Link href="/login"><Bookmark size={16} /><span>북마크</span></Link>}
            {post.type === 'POST' && <button onClick={() => { setOpinion(post.myRepost?.content ?? ''); setRepostError(''); setModal('repost'); }}>
                <Repeat2 size={16} />{post.myRepost ? '퍼가기 관리' : '퍼가기'}
            </button>}
            <button onClick={() => setModal('share')}><Share2 size={16} />외부 공유</button>
            {!post.mine && (user
                ? <button disabled={post.reported} onClick={() => setModal('report')}><Flag size={15} />{post.reported ? '신고됨' : '신고'}</button>
                : <Link href="/login"><Flag size={15} />신고</Link>)}
        </div>
        {modal === 'repost' && <Modal title="내 프로필에 퍼가기" onClose={() => setModal(null)}>
            {user ? <>
                <p>원본 작성자 · {authorHref ? <Link href={authorHref}>{feedAuthorName(post.author)}</Link> : feedAuthorName(post.author)}</p>
                <p className={scope('repost-original-title')}>{post.title}</p>
                <p className={scope('auth-hint')}>내 프로필 ‘포스팅’에 원본과 연결된 글로 표시됩니다. 외부 URL 공유와는 다른 기능입니다.</p>
                <form className={scope('profile-form')} onSubmit={event => { event.preventDefault(); void repost(opinion); }}>
                    <label>의견 (선택)<Textarea rows={4} value={opinion} maxLength={FEED_OPINION_MAX} disabled={pending === 'repost'}
                        onChange={event => setOpinion(event.target.value)} placeholder="함께 나누고 싶은 생각을 더해 보세요." /></label>
                    {repostError && <p className="form-error" role="alert">{repostError}</p>}
                    <div className={scope('modal-actions')}>
                        {post.myRepost
                            ? <Button type="button" variant="secondary" disabled={pending === 'repost'} onClick={() => void cancelRepost()}>퍼가기 취소</Button>
                            : <Button type="button" variant="secondary" disabled={pending === 'repost'} onClick={() => void repost('')}>바로 퍼가기</Button>}
                        <Button type="submit" disabled={pending === 'repost'}>{post.myRepost ? '의견 수정' : '의견 추가 퍼가기'}</Button>
                    </div>
                </form>
            </> : <>
                <p>로그인 후 내 프로필에 퍼갈 수 있습니다.</p>
                <Link className={scope('button')} href="/login">로그인하기</Link>
            </>}
        </Modal>}
        {modal === 'share' && <Modal title="외부 공유 · URL 복사" onClose={() => setModal(null)}>
            <p>게시글 주소를 외부에 공유합니다. 내 프로필에는 퍼간 글이 생기지 않습니다.</p>
            <Input aria-label="게시글 공유 주소" value={url} readOnly onFocus={event => event.target.select()} />
            <div className={scope('modal-actions')}>
                <Button variant="secondary" onClick={() => setModal(null)}>닫기</Button>
                <Button onClick={async () => {
                    try {
                        if (!navigator.clipboard?.writeText) throw new Error();
                        await navigator.clipboard.writeText(url);
                        notify('게시글 URL을 복사했습니다.');
                    } catch { notify('주소를 선택한 뒤 직접 복사해 주세요.'); }
                }}>URL 복사</Button>
            </div>
        </Modal>}
        {modal === 'report' && <FeedReportDialog targetType="post" targetId={post.id} onClose={() => setModal(null)}
            onReported={() => onChange({ ...post, reported: true })} />}
    </>;
}
