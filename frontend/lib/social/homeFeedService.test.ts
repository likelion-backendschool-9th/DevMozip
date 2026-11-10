import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiPost } from '@/tests/helpers/feedApiFixture';
import { fetchFeedPage, parseFeedPost } from './feedApi';
import { fetchOwnProfile } from '@/lib/profiles/profileUpdate';
import { fetchFollowStatus } from '@/lib/profiles/followApi';
import { fetchHomeFeed, homeFeedHref, mergeHomeFeeds } from './homeFeedService';
import type { OwnProfileDetail } from '@/types/profileDetail';
vi.mock('./feedApi', async original => ({ ...await original<typeof import('./feedApi')>(), fetchFeedPage: vi.fn() }));
vi.mock('@/lib/profiles/profileUpdate', () => ({ fetchOwnProfile: vi.fn() }));
vi.mock('@/lib/profiles/followApi', () => ({ fetchFollowStatus: vi.fn() }));
const post = (id: number) => parseFeedPost(apiPost({ id }));
const page = (ids: number[]) => ({ items: ids.map(post), page: 0, size: 12, hasNext: false, totalCount: ids.length });
beforeEach(() => vi.resetAllMocks());

describe('메인 피드 구성', () => {
    it('정렬별 상위 글을 우선 넣고 중복 없이 최대 12개를 채운다', () => {
        const ids = mergeHomeFeeds([[1,5,6,7,8,9,10,11,12,13], [2,1,14,15], [3,2,16,17], [4,3,18,19]].map(ids => ids.map(post))).map(p => p.id);
        expect(ids.slice(0,4)).toEqual([1,2,3,4]);
        expect(ids).toHaveLength(12);
        expect(new Set(ids).size).toBe(12);
    });
    it('1위가 겹치면 다음 순위로 채우고 적은 글·빈 목록도 중복하지 않는다', () => {
        expect(mergeHomeFeeds([[1,2,3,4],[1,2,3],[1,2,3],[1,2,3,4]].map(ids => ids.map(post))).map(p => p.id)).toEqual([1,2,3,4]);
        expect(mergeHomeFeeds([[post(1)],[post(1)],[],[]])).toEqual([post(1)]);
        expect(mergeHomeFeeds([[],[],[],[]])).toEqual([]);
    });
    it.each([null,'INFO','FEEDBACK','PROMO','COMMUNITY'] as const)('카테고리 %s와 seed를 네 정렬 요청에 동일하게 전달한다', async category => {
        vi.mocked(fetchFeedPage).mockResolvedValue(page([1]));
        const signal = new AbortController().signal;
        expect((await fetchHomeFeed('recommended',category,77,false,signal)).items).toEqual([post(1)]);
        expect(vi.mocked(fetchFeedPage).mock.calls.map(c => c[1].sort)).toEqual(['recommended','popular','views','latest']);
        for (const call of vi.mocked(fetchFeedPage).mock.calls) expect(call).toEqual(['recommended',{category,q:'',page:0,size:12,seed:77,sort:call[1].sort},signal]);
    });
    it('비로그인 팔로잉은 서버를 호출하지 않는다', async () => {
        expect(await fetchHomeFeed('following',null,77,false)).toEqual({items:[],followingCount:null});
        expect(fetchFeedPage).not.toHaveBeenCalled();
        expect(fetchOwnProfile).not.toHaveBeenCalled();
    });
    it('로그인 팔로잉은 서버 목록을 최신순으로 사용한다', async () => {
        vi.mocked(fetchFeedPage).mockResolvedValue(page([9,8]));
        expect((await fetchHomeFeed('following','INFO',77,true)).items.map(p => p.id)).toEqual([9,8]);
        expect(fetchFeedPage).toHaveBeenCalledWith('following',expect.objectContaining({sort:'latest',category:'INFO'}),undefined);
        expect(fetchOwnProfile).not.toHaveBeenCalled();
    });
    it.each([0,3])('빈 팔로잉에서 본인의 실제 팔로우 수 %s를 조회한다', async followingCount => {
        vi.mocked(fetchFeedPage).mockResolvedValue(page([]));
        vi.mocked(fetchOwnProfile).mockResolvedValue({tag:'my-tag'} as OwnProfileDetail);
        vi.mocked(fetchFollowStatus).mockResolvedValue({followingCount,followerCount:0,followed:false,activityNotificationEnabled:false});
        expect(await fetchHomeFeed('following',null,77,true)).toEqual({items:[],followingCount});
        expect(fetchFollowStatus).toHaveBeenCalledWith('my-tag',undefined);
    });
    it('태그가 없으면 팔로우 0명으로 단정하지 않는다', async () => {
        vi.mocked(fetchFeedPage).mockResolvedValue(page([]));
        vi.mocked(fetchOwnProfile).mockResolvedValue({tag:null} as OwnProfileDetail);
        expect((await fetchHomeFeed('following',null,77,true)).followingCount).toBeNull();
        expect(fetchFollowStatus).not.toHaveBeenCalled();
    });
    it('조회 실패를 성공 목록으로 숨기지 않는다', async () => {
        vi.mocked(fetchFeedPage).mockRejectedValue(new Error('연결 실패'));
        await expect(fetchHomeFeed('recommended',null,77,false)).rejects.toThrow('연결 실패');
    });
    it('더보기는 탭·카테고리를 유지한다', () => {
        expect(Object.fromEntries(new URL(homeFeedHref('following','FEEDBACK'),'http://localhost').searchParams)).toEqual({mode:'following',sort:'latest',category:'피드백'});
        expect(homeFeedHref('recommended',null)).toBe('/feeds?mode=recommended&sort=recommended');
    });
});
