import { fetchOwnProfile } from '@/lib/profiles/profileUpdate';
import { fetchFollowStatus } from '@/lib/profiles/followApi';
import { categoryLabel, fetchFeedPage } from './feedApi';
import type { FeedCategoryCode, FeedPost, FeedSortValue } from '@/types/feedApi';

export type HomeFeedMode = 'recommended' | 'following';
export const HOME_FEED_LIMIT = 12;
const sorts: FeedSortValue[] = ['recommended', 'popular', 'views', 'latest'];

/** 각 정렬의 가장 높은 미선택 글을 차례로 넣어 상위 글과 추가 탐색을 함께 제공한다. */
export function mergeHomeFeeds(lists: FeedPost[][]): FeedPost[] {
    const result: FeedPost[] = [];
    const seen = new Set<number>();
    const queues = lists.map(items => [...items]);
    while (result.length < HOME_FEED_LIMIT) {
        let added = false;
        for (const queue of queues) {
            let post = queue.shift();
            while (post && seen.has(post.id)) post = queue.shift();
            if (!post) continue;
            seen.add(post.id);
            result.push(post);
            added = true;
            if (result.length === HOME_FEED_LIMIT) break;
        }
        if (!added) break;
    }
    return result;
}

export function homeFeedHref(mode: HomeFeedMode, category: FeedCategoryCode | null) {
    const params = new URLSearchParams({ mode, sort: mode === 'following' ? 'latest' : 'recommended' });
    if (category) params.set('category', categoryLabel(category));
    return `/feeds?${params}`;
}

export async function fetchHomeFeed(mode: HomeFeedMode, category: FeedCategoryCode | null, seed: number,
    signedIn: boolean, signal?: AbortSignal): Promise<{ items: FeedPost[]; followingCount: number | null }> {
    if (mode === 'following' && !signedIn) return { items: [], followingCount: null };
    const query = { category, q: '', page: 0, size: HOME_FEED_LIMIT, seed };
    if (mode === 'recommended') {
        const pages = await Promise.all(sorts.map(sort => fetchFeedPage(mode, { ...query, sort }, signal)));
        return { items: mergeHomeFeeds(pages.map(page => page.items)), followingCount: null };
    }
    const page = await fetchFeedPage(mode, { ...query, sort: 'latest' }, signal);
    if (page.items.length) return { items: page.items, followingCount: null };
    const profile = await fetchOwnProfile(signal);
    // 태그 미설정 계정은 팔로우 수 조회 주소가 없어 게시글 부재만 안내한다.
    const status = profile.tag ? await fetchFollowStatus(profile.tag, signal) : null;
    return { items: [], followingCount: status?.followingCount ?? null };
}
