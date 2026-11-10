'use client';
import Link from 'next/link';
import { likedPostCategoryLabel } from '@/lib/profiles/likedPostsApi';
import { Card } from '@/components/common/Card';
import { Button } from '@/components/common/Button';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import { profileImageUrl } from '@/lib/profiles/profileDetail';
import type { LikedPostsPage, LikedPostAuthor } from '@/types/likedPosts';
import styles from '../page.module.css';
import postStyles from '@/components/posts/PostCard.module.css';
import local from './LikedPostsResult.module.css';
export type LikedPostsResultState = { status: 'loading' } | { status: 'error'; code: number; message: string } | { status: 'ready'; value: LikedPostsPage };
const date = (value: string) => new Date(value).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' });
function AuthorName({ author }: { author: LikedPostAuthor }) {
    const name = author.nickname || '작성자';
    return author.tag && author.tag.toLowerCase() !== 'me' ? <Link href={`/users/${encodeURIComponent(author.tag)}`}><strong>{name}</strong></Link> : <strong>{name}</strong>;
}
export function LikedPostsResult({ result, onRetry, onPage }: { result: LikedPostsResultState; onRetry: () => void; onPage: (page: number) => void }) {
    if (result.status === 'loading') return <p role="status">좋아요한 글을 불러오는 중…</p>;
    if (result.status === 'error') return <div role="alert"><p>{result.message}</p>{result.code === 401
        ? <Link className="button secondary" href="/login?next=/profile/me/likes">로그인하기</Link>
        : <Button variant="secondary" onClick={onRetry}>다시 시도</Button>}</div>;
    const { items, page, totalPages, totalElements } = result.value;
    return <>
        <p className="text-muted">좋아요한 글 {totalElements}개</p>
        <div className={[styles.scope, 'personal-post-list'].join(' ')}>
            {items.map(post => <section key={post.id}>
                <p className={[styles.scope, 'saved-date'].join(' ')}>좋아요한 날짜 · {post.likedAt ? <time dateTime={post.likedAt}>{date(post.likedAt)}</time> : '이전 기록 · 날짜 정보 없음'}</p>
                <Card className={`${postStyles.scope} sns-post-card`}>
                    <header className={`${postStyles.scope} sns-author`}>
                        <ProfileAvatar name={post.author.nickname || '작성자'} color="lavender" small image={profileImageUrl(post.author.profileImage)} />
                        <div><AuthorName author={post.author} />{post.createdAt && <span><time dateTime={post.createdAt}>{date(post.createdAt)}</time></span>}</div>
                        <span className={`${postStyles.scope} sns-category`}>{likedPostCategoryLabel(post.category)}{post.type === 'REPOST' ? ' · 퍼온 글' : ''}</span>
                    </header>
                    {post.contentPreview && <p className={`${postStyles.scope} preserve-text sns-post-body post-excerpt`}>{post.contentPreview}</p>}
                    {post.originalPost && <blockquote className={local.quote}><AuthorName author={post.originalPost.author} /><p className="preserve-text">{post.originalPost.contentPreview || '내용 없음'}</p></blockquote>}
                    <Link href={`/feeds/${post.id}`}>전체 글 보기 →</Link>
                </Card>
            </section>)}
        </div>
        {!items.length && <div className={[styles.scope, 'empty-state'].join(' ')}>
            <h2>{totalElements === 0 ? '아직 좋아요한 글이 없어요.' : '이 페이지에는 표시할 글이 없어요.'}</h2>
            {totalElements === 0 ? <><p>SNS 피드에서 마음에 드는 글에 좋아요를 눌러보세요.</p><Link href="/feeds" className="button secondary">SNS 둘러보기</Link></>
                : <Button variant="secondary" onClick={() => onPage(0)}>첫 페이지 보기</Button>}
        </div>}
        {totalPages > 0 && <nav aria-label="좋아요한 글 페이지" className={local.pagination}>
            <Button variant="secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>이전</Button>
            <span>{page + 1} / {totalPages}</span>
            <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onPage(page + 1)}>다음</Button>
        </nav>}
    </>;
}
