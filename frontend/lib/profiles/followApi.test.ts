import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    appendFollowUsers, changeFollow, fetchFollowList, fetchFollowStatus, FollowRequestError, followListQuery, followListSearch,
    followTab, followUserName, parseFollowPage, parseFollowResult, parseFollowStatus, updateFollowNotification,
} from './followApi';
import type { FollowUser } from '@/types/followApi';

const person = (tag: string | null, extra: Partial<FollowUser> = {}): FollowUser => ({
    tag, nickname: '개발자', profileImage: null, jobCategories: ['백엔드 개발자'], career: '2년 차', skills: ['Java'],
    collaborationStatus: 'AVAILABLE', followed: false, activityNotificationEnabled: false, ...extra,
});
const page = () => ({ page: 0, size: 20, hasNext: false, totalCount: 2,
    items: [{ ...person('dev-one'), email: 'private@test.local', id: 7 }, person(null, { nickname: null, skills: [] })] });
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });

describe('팔로우 응답 해석', () => {
    it('목록에서 공개 필드만 남기고 태그·닉네임의 NULL을 허용한다', () => {
        const value = parseFollowPage(page());
        expect(value.items[1].tag).toBeNull();
        expect(value.items[1].nickname).toBeNull();
        expect(JSON.stringify(value)).not.toMatch(/private|"id"|email/);
        expect(followUserName(value.items[1])).toBe('이름 없는 사용자');
    });
    it.each([
        (p: ReturnType<typeof page>) => { p.items.push({ ...person('dev-one'), email: '', id: 1 }); p.totalCount = 3; },
        (p: ReturnType<typeof page>) => { p.items[0].tag = '../admin'; },
        (p: ReturnType<typeof page>) => { p.items[0].activityNotificationEnabled = true; },
        (p: ReturnType<typeof page>) => { p.items[0].skills = [1 as unknown as string]; },
        (p: ReturnType<typeof page>) => { p.totalCount = 1; },
        (p: ReturnType<typeof page>) => { p.size = 101; },
        (p: ReturnType<typeof page>) => { (p as Record<string, unknown>).hasNext = 'no'; },
    ])('모순되거나 안전하지 않은 목록 응답을 거절한다', mutate => {
        const p = page();
        mutate(p);
        expect(() => parseFollowPage(p)).toThrow();
    });
    it('팔로우하지 않은 관계의 알림 켜짐과 음수 수를 거절한다', () => {
        expect(() => parseFollowStatus({ followerCount: 1, followingCount: 0, followed: false, activityNotificationEnabled: true })).toThrow();
        expect(() => parseFollowStatus({ followerCount: -1, followingCount: 0, followed: false, activityNotificationEnabled: false })).toThrow();
        expect(() => parseFollowResult({ followed: true, activityNotificationEnabled: false, followerCount: 0 })).toThrow();
        expect(parseFollowResult({ followed: true, activityNotificationEnabled: true, followerCount: 3, extra: 'x' }))
            .toEqual({ followed: true, activityNotificationEnabled: true, followerCount: 3 });
    });
});

describe('목록 조건', () => {
    it('탭 기본값은 팔로잉이다', () => {
        expect(followTab(new URLSearchParams())).toBe('following');
        expect(followTab(new URLSearchParams('tab=followers'))).toBe('followers');
        expect(followTab(new URLSearchParams('tab=other'))).toBe('following');
    });
    it('기본값과 허용 경계를 쓴다', () => {
        expect(followListQuery(new URLSearchParams())).toEqual({ q: '', available: false, page: 0, size: 20 });
        expect(followListQuery(new URLSearchParams(`q=${'가'.repeat(100)}&available=true&page=3&size=100`)))
            .toEqual({ q: '가'.repeat(100), available: true, page: 3, size: 100 });
        expect(followListQuery(new URLSearchParams('q=%20react%20'))?.q).toBe('react');
    });
    it.each(['page=-1', 'page=01', 'size=0', 'size=101', 'page=1&page=2', 'q=a&q=b', 'available=yes', 'page=1234567890', `q=${'가'.repeat(101)}`])(
        '잘못된 조건 %s', query => expect(followListQuery(new URLSearchParams(query))).toBeNull());
    it('검색어가 없으면 백엔드에 넘기지 않는다', () => {
        expect(followListSearch({ q: '  ', available: false, page: 0, size: 20 })).toBe('page=0&size=20');
        expect(followListSearch({ q: ' 리액트 ', available: true, page: 1, size: 20 })).toBe('q=%EB%A6%AC%EC%95%A1%ED%8A%B8&available=true&page=1&size=20');
    });
    it('다음 페이지에서 이미 있는 태그는 다시 넣지 않는다', () => {
        const merged = appendFollowUsers([person('a'), person(null)], [person('a', { nickname: '중복' }), person('b'), person(null)]);
        expect(merged.map(item => item.tag)).toEqual(['a', null, 'b', null]);
        expect(merged[0].nickname).toBe('개발자');
    });
});

describe('브라우저 요청', () => {
    it('BFF 경로만 부르고 사용자 ID를 보내지 않는다', async () => {
        const fetch = vi.fn(async (_url: string, init?: RequestInit) => Response.json(init?.method === 'PUT'
            ? { activityNotificationEnabled: true }
            : init?.method ? { followed: init.method === 'POST', activityNotificationEnabled: false, followerCount: init.method === 'POST' ? 1 : 0 }
                : { followerCount: 0, followingCount: 0, followed: false, activityNotificationEnabled: false }));
        vi.stubGlobal('fetch', fetch);
        await fetchFollowStatus('dev-one');
        expect((await changeFollow('dev-one', true)).followed).toBe(true);
        expect((await changeFollow('dev-one', false)).followed).toBe(false);
        expect((await updateFollowNotification('dev-one', true)).activityNotificationEnabled).toBe(true);
        expect(fetch.mock.calls.map(([url, init]) => `${init?.method ?? 'GET'} ${url}`)).toEqual([
            'GET /api/profiles/dev-one/follow', 'POST /api/profiles/dev-one/follow', 'DELETE /api/profiles/dev-one/follow', 'PUT /api/profiles/dev-one/notify']);
        expect(fetch.mock.calls[3][1]?.body).toBe('{"activityNotificationEnabled":true}');
    });
    it('오류 상태와 BFF 문구를 그대로 전달한다', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => Response.json({ message: '팔로우한 개발자의 알림만 설정할 수 있어요.' }, { status: 409 })));
        const error = await updateFollowNotification('dev-one', true).catch(e => e);
        expect(error).toBeInstanceOf(FollowRequestError);
        expect(error.status).toBe(409);
        expect(error.message).toBe('팔로우한 개발자의 알림만 설정할 수 있어요.');
    });
    it('요청한 페이지와 다른 응답을 받지 않는다', async () => {
        vi.stubGlobal('fetch', vi.fn(async () => Response.json({ ...page(), page: 1 })));
        await expect(fetchFollowList('dev-one', 'followers', { q: '', available: false, page: 0, size: 20 })).rejects.toThrow();
    });
});
