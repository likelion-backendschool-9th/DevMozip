'use client';
import styles from './FeedPreview.module.css';
import { useAuth } from '@/lib/state/authStore';
import { useCallback, useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { ArrowUpRight, ChevronLeft, ChevronRight, Heart, MessageCircle } from 'lucide-react';
import { categoryLabel, FEED_CATEGORIES, feedAuthorHref, feedAuthorName, FeedRequestError } from '@/lib/social/feedApi';
import { fetchHomeFeed, homeFeedHref, type HomeFeedMode } from '@/lib/social/homeFeedService';
import { recommendationSeed } from '@/lib/social/socialService';
import { ProfileAvatar } from '@/components/profile/ProfileAvatar';
import type { FeedCategoryCode, FeedPost } from '@/types/feedApi';

type ListState = { key: string; items: FeedPost[]; followingCount: number | null; error: string; unauthorized: boolean };
const empty: ListState = { key: '', items: [], followingCount: null, error: '', unauthorized: false };

export function FeedPreview() {
    const { user } = useAuth();
    const [mode, setMode] = useState<HomeFeedMode>('recommended');
    const [category, setCategory] = useState<FeedCategoryCode | null>(null);
    const [seed] = useState(recommendationSeed);
    const [attempt, setAttempt] = useState(0);
    const [list, setList] = useState<ListState>(empty);
    const track = useRef<HTMLDivElement>(null);
    const [edges, setEdges] = useState({ start: true, end: true });
    const email = user?.email ?? '';
    const signedIn = !!user;
    const key = JSON.stringify([mode, category, seed, email, attempt]);
    const current = list.key === key;
    const needsLogin = mode === 'following' && (!signedIn || current && list.unauthorized);
    const items = current && !needsLogin ? list.items : [];
    const loading = !current && !needsLogin;
    const href = homeFeedHref(mode, category);
    const loginHref = `/login?next=${encodeURIComponent(href)}`;

    useEffect(() => {
        if (mode === 'following' && !signedIn) return;
        const controller = new AbortController();
        void fetchHomeFeed(mode, category, seed, signedIn, controller.signal).then(result => {
            if (!controller.signal.aborted) setList({ ...empty, ...result, key });
        }).catch(error => {
            if (!controller.signal.aborted) setList({ ...empty, key,
                unauthorized: error instanceof FeedRequestError && error.status === 401,
                error: error instanceof Error ? error.message : '피드를 불러오지 못했어요.' });
        });
        return () => controller.abort();
    }, [key, mode, category, seed, signedIn]);

    const measure = useCallback(() => {
        const el = track.current;
        if (el) setEdges({ start: el.scrollLeft <= 2, end: el.scrollLeft + el.clientWidth >= el.scrollWidth - 2 });
    }, []);
    useEffect(() => {
        const el = track.current;
        if (!el) return;
        el.scrollTo({ left: 0 });
        measure();
        const observer = new ResizeObserver(measure);
        observer.observe(el);
        return () => observer.disconnect();
    }, [key, items.length, measure]);
    const slide = (direction: number) => {
        const el = track.current;
        if (!el) return;
        const width = el.firstElementChild?.getBoundingClientRect().width ?? 320;
        el.scrollBy({ left: direction * (width + 18),
            behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
    };

    return <section className={styles.section} aria-label="메인 SNS 피드">
        <div className={styles.heading}>
            <div><div className={styles.eyebrow}>DEV STORIES & INSIGHTS</div>
                <h2>개발자의 오늘, 함께 나누는 이야기</h2>
                <p>새로운 발견부터 작은 고민까지, 동료들의 이야기를 만나보세요.</p></div>
            <Link className={styles.more} href={href}>전체 피드 더보기 <ArrowUpRight size={16} /></Link>
        </div>
        <div className={styles.toolbar}>
            <div className={styles.tabs} aria-label="피드 종류">
                {([{ value: 'recommended', label: '추천' }, { value: 'following', label: '팔로잉' }] as const).map(tab =>
                    <button type="button" key={tab.value} aria-pressed={mode === tab.value} onClick={() => setMode(tab.value)}>{tab.label}</button>)}
            </div>
            <div className={styles.categories} aria-label="게시글 카테고리">
                {[{ code: null, label: '전체' }, ...FEED_CATEGORIES].map(item =>
                    <button type="button" key={item.code ?? 'all'} aria-pressed={category === item.code}
                        onClick={() => setCategory(item.code)}>{item.label}</button>)}
            </div>
            <div className={styles.controls}>
                <button type="button" aria-label="이전 게시글" disabled={edges.start || !items.length} onClick={() => slide(-1)}><ChevronLeft size={19} /></button>
                <button type="button" aria-label="다음 게시글" disabled={edges.end || !items.length} onClick={() => slide(1)}><ChevronRight size={19} /></button>
            </div>
        </div>
        {needsLogin ? <div className={styles.empty}>
            <strong>팔로우한 개발자의 소식을 보려면 로그인해 주세요.</strong>
            <p>로그인하면 내가 팔로우한 개발자의 이야기를 모아 볼 수 있어요.</p>
            <Link className={styles.action} href={loginHref}>로그인 <ArrowUpRight size={14} /></Link>
        </div> : loading ? <div className={styles.empty} role="status">피드를 불러오는 중이에요.</div>
            : list.error ? <div className={styles.empty} role="alert"><strong>피드를 불러오지 못했어요.</strong><p>{list.error}</p>
                <button type="button" className={styles.action} onClick={() => setAttempt(value => value + 1)}>다시 시도</button></div>
                : !items.length ? <div className={styles.empty}>
                    <strong>{mode === 'following' ? list.followingCount === 0 ? '아직 팔로우한 개발자가 없어요.'
                        : category ? '이 카테고리에는 팔로우한 개발자의 게시글이 아직 없어요.' : '팔로우한 개발자의 게시글이 아직 없어요.'
                        : '아직 이 카테고리의 소식이 없어요.'}</strong>
                    <p>{mode === 'following' && list.followingCount === 0 ? '관심 있는 개발자를 팔로우하고 새로운 소식을 만나보세요.' : '다른 카테고리나 추천 피드를 선택해 보세요.'}</p>
                    {mode === 'following' && list.followingCount === 0 && <Link className={styles.action} href="/explore">개발자 탐색 <ArrowUpRight size={14} /></Link>}
                </div> : <div className={styles.track} ref={track} onScroll={measure} tabIndex={0} role="region" aria-label="SNS 게시글 가로 슬라이드">
                    {items.map(post => {
                        const authorHref = feedAuthorHref(post.author);
                        const authorName = feedAuthorName(post.author);
                        const author = <><ProfileAvatar name={authorName} color="lavender" small image={post.author.profileImage ?? undefined} /><strong>{authorName}</strong></>;
                        const tags = [...new Set([...post.techStacks, ...post.interests])].slice(0, 2);
                        return <article className={styles.card} key={post.id}>
                            <Link className={styles.visual} href={`/feeds/${post.id}`} aria-label={`${post.title ?? '게시글'} 읽기`}>
                                {post.imageUrls[0] && <img src={post.imageUrls[0]} alt="" loading="lazy" />}
                                <span>{categoryLabel(post.category)}</span>
                                {!post.imageUrls.length && <strong aria-hidden="true">{'</>'}</strong>}
                            </Link>
                            <div className={styles.content}>
                                {authorHref ? <Link className={styles.author} href={authorHref}>{author}</Link> : <div className={styles.author}>{author}</div>}
                                <span className={styles.job}>{post.author.jobCategories[0]}</span>
                                <Link className={styles.title} href={`/feeds/${post.id}`}><h3>{post.title}</h3></Link>
                                <p className={styles.excerpt}>{post.content}</p>
                                <div className={styles.tags}>{tags.map(tag => <span key={tag}>#{tag}</span>)}</div>
                                <div className={styles.bottom}>
                                    <span aria-label={`좋아요 ${post.likeCount}개`}><Heart size={14} />{post.likeCount}</span>
                                    <span aria-label={`댓글 ${post.commentCount}개`}><MessageCircle size={14} />{post.commentCount}</span>
                                    <Link href={`/feeds/${post.id}`}>이야기 읽기 <ArrowUpRight size={14} /></Link>
                                </div>
                            </div>
                        </article>;
                    })}
                    <Link className={styles.endCard} href={href}><ArrowUpRight size={24} /><strong>이 조건의 피드 더 보기</strong><span>더 많은 이야기를 만나보세요.</span></Link>
                </div>}
    </section>;
}
