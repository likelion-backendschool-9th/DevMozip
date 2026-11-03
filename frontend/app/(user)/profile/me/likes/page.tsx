'use client';
import styles from './page.module.css';
import Link from 'next/link';
import { useAuth } from '@/lib/state/authStore';
import { useProfiles } from '@/lib/state/profileStore';
import { PostCard } from '@/components/posts/PostCard';
import { likedPosts } from '@/lib/social/personalLibraryService';
export default function LikedPostsPage() {
    const { user, canViewProfile } = useAuth();
    const { state } = useProfiles();
    const items = user ? likedPosts(state, user.id).filter(i => canViewProfile(i.post.authorId)) : [];
    return (
        <>
            <header className={[styles.scope, 'personal-page-heading'].join(' ')}>
                <span className={[styles.scope, 'eyebrow'].join(' ')}>MY FAVORITE STORIES</span>
                <h1>좋아요한 글</h1>
                <p>마음에 담아 둔 동료들의 이야기를 다시 만나보세요.</p>
            </header>
            <div className={[styles.scope, 'personal-post-list'].join(' ')}>
                {items.map(({ post, likedAt }) => (
                    <section key={post.id}>
                        <p className={[styles.scope, 'saved-date'].join(' ')}>
                            좋아요한 날짜 ·{' '}
                            {likedAt ? (
                                <time dateTime={likedAt}>
                                    {new Date(likedAt).toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' })}
                                </time>
                            ) : (
                                '이전 기록 · 날짜 정보 없음'
                            )}
                        </p>
                        <PostCard post={post} />
                    </section>
                ))}
                {!items.length && (
                    <div className={[styles.scope, 'empty-state'].join(' ')}>
                        <h2>아직 좋아요한 글이 없어요.</h2>
                        <p>SNS 피드에서 마음에 드는 글에 좋아요를 눌러보세요.</p>
                        <Link href="/feeds" className={[styles.scope, 'button secondary'].join(' ')}>
                            SNS 둘러보기
                        </Link>
                    </div>
                )}
            </div>
        </>
    );
}
