'use client';
import styles from '@/components/posts/PostCard.module.css';
import ownerStyles from '@/components/posts/PostOwnerActions.module.css';
import local from './FeedPostCard.module.css';
import { useState } from 'react';
import Link from 'next/link';
import { Eye, Pencil, Trash2 } from 'lucide-react';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { usePrototype } from '@/lib/state/prototypeStore';
import { categoryLabel, deleteFeedPost, feedAuthorHref, feedAuthorName } from '@/lib/social/feedApi';
import { Card } from '@/components/common/Card';
import { Modal } from '@/components/common/Modal';
import { Button } from '@/components/common/Button';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import type { FeedAuthor, FeedPost } from '@/types/feedApi';
import { FeedPostActions } from './FeedPostActions';

const scope = (name: string) => [styles.scope, name].join(' ');
const date = (value: string) => new Date(value).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' });

function Author({ author, meta }: { author: FeedAuthor; meta: string }) {
    const href = feedAuthorHref(author);
    const name = feedAuthorName(author);
    const avatar = <ProfileAvatar name={name} color="lavender" small image={author.profileImage ?? undefined} />;
    return <>
        {href ? <Link href={href}>{avatar}</Link> : avatar}
        <div>
            {href ? <Link href={href}><strong>{name}</strong></Link> : <strong>{name}</strong>}
            <span>{meta}</span>
        </div>
    </>;
}

/**
 * 피드 API 게시글 카드. 모양은 Mock 화면의 components/posts/PostCard와 같다.
 * 작성자 숨김(WBS 3.8)은 프로필 담당 API가 아직 없어 수정·삭제 버튼만 둔다.
 */
export function FeedPostCard({ post, full = false, onChange, onRemoved }: {
    post: FeedPost;
    full?: boolean;
    onChange: (next: FeedPost) => void;
    onRemoved?: () => void;
}) {
    const navigate = useNavigate();
    const { notify } = usePrototype();
    const [remove, setRemove] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const hidden = post.visibility !== 'VISIBLE';
    const job = post.author.jobCategories[0];
    const meta = [job, `${date(post.createdAt)}${post.edited ? ' · 수정됨' : ''}`].filter(Boolean).join(' · ');

    async function removePost() {
        if (busy) return;
        setBusy(true);
        setError('');
        try {
            await deleteFeedPost(post.id);
            setRemove(false);
            notify('게시글을 삭제했어요.');
            if (full) navigate('/feeds');
            else onRemoved?.();
        } catch (failure) { setError(failure instanceof Error ? failure.message : '게시글을 삭제하지 못했어요.'); }
        finally { setBusy(false); }
    }

    return <Card className={scope(`sns-post-card ${full ? 'full-post' : ''}`)}>
        <header className={scope('sns-author')}>
            <Author author={post.author} meta={meta} />
            <span className={scope('sns-category')}>
                {post.type === 'REPOST' ? '퍼간 글' : categoryLabel(post.category)}{hidden ? ' · 숨김' : ''}
            </span>
            {post.mine && <div className={ownerStyles.actions} aria-label="포스팅 관리">
                {post.type === 'POST' && <Link href={`/feeds/${post.id}/edit`} aria-label="게시글 수정" title="게시글 수정"><Pencil size={18} /></Link>}
                <button type="button" className={ownerStyles.remove} aria-label={post.type === 'REPOST' ? '퍼간 글 삭제' : '삭제'}
                    title={post.type === 'REPOST' ? '퍼간 글 삭제' : '삭제'} onClick={() => setRemove(true)}><Trash2 size={18} /></button>
            </div>}
        </header>
        {post.type === 'REPOST' ? <>
            {post.content && <p className={[scope(`preserve-text sns-post-body ${full ? '' : 'post-excerpt'}`), local.opinion].join(' ')}>{post.content}</p>}
            {post.original?.available
                ? <Card className={[scope('sns-post-card'), local.original].join(' ')}>
                    <Link href={`/feeds/${post.original.id}`}><h2>{post.original.title}</h2></Link>
                    <p className={[scope('sns-post-body'), local.originalMeta].join(' ')}>원본 · {feedAuthorName(post.original.author)} · {categoryLabel(post.original.category)}</p>
                </Card>
                : <p className={['auth-hint', local.opinion].join(' ')}>원본 글이 삭제되었거나 볼 수 없는 상태입니다.</p>}
        </> : <>
            {full ? <h1>{post.title}</h1> : <Link href={`/feeds/${post.id}`}><h2>{post.title}</h2></Link>}
            <p className={scope(`preserve-text sns-post-body ${full ? '' : 'post-excerpt'}`)}>{post.content}</p>
            {post.imageUrls.length > 0 && <div className={scope('sns-images')}>
                {post.imageUrls.map((url, index) => <div key={url}><img src={url} alt={`${post.title ?? '게시글'} 이미지 ${index + 1}`} loading="lazy" /></div>)}
            </div>}
            <div className={scope('tags')}>{post.techStacks.map(tag => <span key={tag}>#{tag}</span>)}</div>
            {post.interests.length > 0 && <div className={styles.interests} aria-label="관심 분야">
                {post.interests.map(field => <span key={field}>{field}</span>)}
            </div>}
        </>}
        <div className={scope('sns-post-meta')}>
            <span><Eye size={13} />조회 {post.viewCount}</span>
            {!full && <Link href={`/feeds/${post.id}`}>전체 글 보기 →</Link>}
        </div>
        <FeedPostActions post={post} onChange={onChange} />
        {remove && <Modal title={post.type === 'REPOST' ? '퍼간 글을 삭제할까요?' : '게시글을 삭제할까요?'} onClose={() => setRemove(false)}>
            <p>{post.type === 'REPOST' ? '내 프로필에서 퍼간 글이 사라집니다. 원본 글은 그대로 남습니다.'
                : '피드와 상세 화면에서 사라지며, 연결된 퍼간 글에는 원본 삭제 안내가 표시됩니다.'}</p>
            {error && <p className="form-error" role="alert">{error}</p>}
            <div className={scope('modal-actions')}>
                <Button variant="secondary" disabled={busy} onClick={() => setRemove(false)}>취소</Button>
                <Button disabled={busy} onClick={() => void removePost()}>{busy ? '삭제하는 중…' : '삭제'}</Button>
            </div>
        </Modal>}
    </Card>;
}
