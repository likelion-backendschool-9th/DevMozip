'use client';
import styles from './page.module.css';
import { useEffect, useState } from 'react';
import Link from 'next/link';
import { useQueryState as useSearchParams } from '@/lib/navigation';
import { PenLine, Search } from 'lucide-react';
import { useAuth } from '@/lib/state/authStore';
import { categories } from '@/mocks/home';
import { feedSortOptions } from '@/mocks/social';
import { parseFeedFilters, recommendationSeed } from '@/lib/social/socialService';
import { appendFeedPosts, categoryCode, FEED_PAGE_SIZE, FEED_QUERY_MAX, fetchFeedPage } from '@/lib/social/feedApi';
import { FeedPostCard } from '@/app/feeds/_components/FeedPostCard';
import { Input } from '@/components/common/Input';
import { Button } from '@/components/common/Button';
import type { FeedListQuery, FeedPost } from '@/types/feedApi';

type ListState = {
    key: string;
    items: FeedPost[];
    page: number;
    hasNext: boolean;
    totalCount: number;
    error: string;
    loadingMore: boolean;
    moreError: string;
};
const empty: ListState = { key: '', items: [], page: 0, hasNext: false, totalCount: 0, error: '', loadingMore: false, moreError: '' };
const scope = (name: string) => [styles.scope, name].join(' ');
const message = (error: unknown, fallback: string) => error instanceof Error ? error.message : fallback;

/**
 * SNS 피드 (8.1~8.3). 추천 탭은 8.2 추천 피드, 팔로잉 탭은 8.2 팔로잉 피드를 부른다.
 * 카테고리·검색어·정렬은 주소(?mode=&category=&q=&sort=)에 두고 두 탭 모두에 적용한다.
 * 다음 페이지는 더 보기 버튼으로 불러와 기존 목록 뒤에 붙인다. 추천순은 세션마다 정한 seed를 다음 페이지에도 보낸다.
 */
export default function FeedPage() {
    const { user } = useAuth();
    const [params, setParams] = useSearchParams();
    const filters = parseFeedFilters(params);
    const [q, setQ] = useState(filters.q);
    const [seed] = useState(recommendationSeed);
    // 주소의 검색어가 바뀌면 입력창도 맞춘다(렌더 중 이전 값과 비교).
    const [shownQ, setShownQ] = useState(filters.q);
    if (shownQ !== filters.q) {
        setShownQ(filters.q);
        setQ(filters.q);
    }
    const [attempt, setAttempt] = useState(0);
    const mode = filters.mode === 'following' ? 'following' : 'recommended';
    const category = categoryCode(filters.category);
    const email = user?.email ?? '';
    const needsLogin = mode === 'following' && !user;
    const key = JSON.stringify([mode, category, filters.q, filters.sort, seed, email, attempt]);
    const [list, setList] = useState<ListState>(empty);

    const query = (page: number): FeedListQuery => ({ category, q: filters.q, sort: filters.sort, page, size: FEED_PAGE_SIZE, seed });

    useEffect(() => {
        if (needsLogin) return;
        const controller = new AbortController();
        const first: FeedListQuery = { category, q: filters.q, sort: filters.sort, page: 0, size: FEED_PAGE_SIZE, seed };
        void fetchFeedPage(mode, first, controller.signal).then(page => {
            if (!controller.signal.aborted) setList({ ...empty, key, items: page.items, hasNext: page.hasNext, totalCount: page.totalCount });
        }).catch(error => {
            if (!controller.signal.aborted) setList({ ...empty, key, error: message(error, '피드를 불러오지 못했어요.') });
        });
        return () => controller.abort();
    }, [key, needsLogin, mode, category, filters.q, filters.sort, seed]);

    async function loadMore() {
        if (list.loadingMore || !list.hasNext) return;
        const current = list.key;
        setList(state => ({ ...state, loadingMore: true, moreError: '' }));
        try {
            const page = await fetchFeedPage(mode, query(list.page + 1));
            setList(state => state.key !== current ? state : { ...state, items: appendFeedPosts(state.items, page.items), page: page.page,
                hasNext: page.hasNext, totalCount: page.totalCount, loadingMore: false });
        } catch (error) {
            setList(state => state.key !== current ? state : { ...state, loadingMore: false, moreError: message(error, '다음 이야기를 불러오지 못했어요.') });
        }
    }

    const update = (name: string, value: string) => {
        const next = new URLSearchParams(params);
        next.set(name, value);
        if (name === 'mode') next.delete('sort');
        setParams(next);
    };
    const replaceItem = (post: FeedPost) => setList(state => ({ ...state, items: state.items.map(item => item.id === post.id ? post : item) }));
    const removeItem = (id: number) => setList(state => ({ ...state, items: state.items.filter(item => item.id !== id),
        totalCount: Math.max(0, state.totalCount - 1) }));
    const loading = !needsLogin && list.key !== key;
    const ready = !needsLogin && !loading && !list.error;

    return (
        <div className={scope('container sns-feed-page')}>
            <header className={scope('section-heading')}>
                <div>
                    <div className={scope('eyebrow')}>DEV STORIES & INSIGHTS</div>
                    <h1>개발자의 오늘을 나누는 공간</h1>
                    <p>발견한 지식, 함께 고민할 문제, 작은 성취를 공유해요.</p>
                </div>
                <Link className={scope('button')} href={user ? '/feeds/new' : '/login'}>
                    <PenLine size={15} />글 작성
                </Link>
            </header>
            <div className={scope('sns-feed-layout')}>
                <section className={scope('sns-feed-main')}>
                    <div className={scope('sns-feed-controls')}>
                        <div className={scope('tabs')}>
                            {[
                                { value: 'recommended', label: '추천' },
                                { value: 'following', label: '팔로잉' },
                            ].map(m => (
                                <button
                                    key={m.value}
                                    className={scope(filters.mode === m.value ? 'active' : '')}
                                    aria-pressed={filters.mode === m.value}
                                    onClick={() => update('mode', m.value)}
                                >
                                    {m.label}
                                </button>
                            ))}
                        </div>
                        <label className={scope('sr-only')} htmlFor="feed-sort">
                            게시글 정렬
                        </label>
                        <select
                            id="feed-sort"
                            value={filters.sort}
                            onChange={e => update('sort', e.target.value)}
                            className={scope('form-input')}
                        >
                            {feedSortOptions.map(o => (
                                <option key={o.value} value={o.value}>
                                    {o.label}
                                </option>
                            ))}
                        </select>
                    </div>
                    <div className={scope('sns-feed-filter-row')}>
                        <div className={scope('chips')}>
                            {categories.map(c => (
                                <button
                                    className={scope(`chip ${filters.category === c ? 'selected' : ''}`)}
                                    aria-pressed={filters.category === c}
                                    key={c}
                                    onClick={() => update('category', c)}
                                >
                                    {c}
                                </button>
                            ))}
                        </div>
                        <form
                            onSubmit={e => {
                                e.preventDefault();
                                update('q', q.trim());
                            }}
                        >
                            <Input
                                type="search"
                                aria-label="게시글 검색"
                                value={q}
                                maxLength={FEED_QUERY_MAX}
                                onChange={e => setQ(e.target.value)}
                                placeholder="게시글 검색"
                            />
                            <button className={scope('icon-button')} type="submit" aria-label="게시글 검색 실행">
                                <Search size={15} />
                            </button>
                        </form>
                    </div>
                    <p className={scope('sns-result-count')} role="status">
                        {loading ? '이야기를 불러오는 중이에요.' : ready ? `${list.totalCount}개의 이야기${filters.sort === 'recommended' ? ' · 추천 순서' : ''}` : ''}
                    </p>
                    <div className={scope('sns-vertical-feed')}>
                        {ready && list.items.map(p => (
                            <FeedPostCard key={p.id} post={p} onChange={replaceItem} onRemoved={() => removeItem(p.id)} />
                        ))}
                    </div>
                    {!needsLogin && !loading && list.error && (
                        <div className={scope('empty-state')} role="alert">
                            <strong>{list.error}</strong>
                            <p>잠시 후 다시 시도해 주세요.</p>
                            <Button variant="secondary" onClick={() => setAttempt(value => value + 1)}>다시 시도</Button>
                        </div>
                    )}
                    {(needsLogin || (ready && !list.items.length)) && (
                        <div className={scope('empty-state')}>
                            <strong>{needsLogin ? '로그인하고 팔로잉 피드를 확인하세요.' : '조건에 맞는 이야기가 없습니다.'}</strong>
                            <p>다른 카테고리나 검색어를 선택해 보세요.</p>
                            {needsLogin ? (
                                <Link className={scope('button')} href="/login">
                                    로그인
                                </Link>
                            ) : (
                                <Button
                                    variant="secondary"
                                    onClick={() => {
                                        setQ('');
                                        setParams({});
                                    }}
                                >
                                    조건 초기화
                                </Button>
                            )}
                        </div>
                    )}
                    {ready && list.hasNext && (
                        <div className={styles.loadMore}>
                            {list.moreError && <p role="alert">{list.moreError}</p>}
                            <Button variant="secondary" disabled={list.loadingMore} onClick={() => void loadMore()}>
                                {list.loadingMore ? '불러오는 중…' : '이야기 더 보기'}
                            </Button>
                        </div>
                    )}
                </section>
                <aside className={scope('sns-feed-aside')}>
                    <div>
                        <span className={scope('eyebrow')}>SHARE & GROW</span>
                        <h2>
                            좋은 경험은
                            <br />
                            나눌수록 커지니까.
                        </h2>
                        <p>서로를 존중하는 이야기로 더 나은 개발 문화를 만들어가요.</p>
                        <Link href={user ? '/profile/me?tab=posts' : '/login'}>내 포스팅 보기 →</Link>
                    </div>
                    <p className={scope('auth-hint')}>
                        추천은 세션마다 섞은 순서입니다. AI 추천이나 외부 API는 사용하지 않습니다.
                    </p>
                </aside>
            </div>
        </div>
    );
}
