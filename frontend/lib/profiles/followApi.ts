import type {
    FollowListQuery, FollowListTab, FollowNotification, FollowPage, FollowResult, FollowStatus, FollowUser,
} from '@/types/followApi';
import { validProfileSlug } from './profileDetail';

// 팔로우 API 응답 해석과 브라우저 요청. 브라우저는 BFF(/api/profiles/{slug}/...)만 부르고 사용자 ID를 보내지 않는다.

export const FOLLOW_QUERY_MAX = 100;
export const FOLLOW_PAGE_SIZE = 20;
const MAX_SIZE = 100;

const count = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const flag = (v: unknown): v is boolean => typeof v === 'boolean';
const text = (v: unknown): v is string | null => v === null || typeof v === 'string';
const strings = (v: unknown): v is string[] => Array.isArray(v) && v.every(s => typeof s === 'string');
const record = (v: unknown) => {
    if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('팔로우 응답 형식 오류');
    return v as Record<string, unknown>;
};
// 팔로우하지 않은 관계에는 알림 설정이 있을 수 없다.
const relation = (followed: unknown, notification: unknown) =>
    flag(followed) && flag(notification) && (followed || !notification);

export function parseFollowStatus(value: unknown): FollowStatus {
    const v = record(value);
    if (!count(v.followerCount) || !count(v.followingCount) || !relation(v.followed, v.activityNotificationEnabled)) throw new Error('팔로우 상태 응답 오류');
    return { followerCount: v.followerCount, followingCount: v.followingCount,
        followed: v.followed as boolean, activityNotificationEnabled: v.activityNotificationEnabled as boolean };
}

export function parseFollowResult(value: unknown): FollowResult {
    const v = record(value);
    if (!count(v.followerCount) || !relation(v.followed, v.activityNotificationEnabled)
        || (v.followed && v.followerCount === 0)) throw new Error('팔로우 결과 응답 오류');
    return { followed: v.followed as boolean, activityNotificationEnabled: v.activityNotificationEnabled as boolean, followerCount: v.followerCount };
}

export function parseFollowNotification(value: unknown): FollowNotification {
    const v = record(value);
    if (!flag(v.activityNotificationEnabled)) throw new Error('알림 설정 응답 오류');
    return { activityNotificationEnabled: v.activityNotificationEnabled };
}

function parseFollowUser(value: unknown): FollowUser {
    const v = record(value);
    if (!(v.tag === null || typeof v.tag === 'string' && validProfileSlug(v.tag)) || !text(v.nickname) || !text(v.profileImage)
        || !strings(v.jobCategories) || !text(v.career) || !strings(v.skills) || !text(v.collaborationStatus)
        || !relation(v.followed, v.activityNotificationEnabled)) throw new Error('팔로우 목록 항목 오류');
    // 알 수 없는 추가 필드(이메일 등)는 브라우저 응답에 넣지 않는다.
    return { tag: v.tag as string | null, nickname: v.nickname, profileImage: v.profileImage,
        jobCategories: v.jobCategories, career: v.career, skills: v.skills, collaborationStatus: v.collaborationStatus,
        followed: v.followed as boolean, activityNotificationEnabled: v.activityNotificationEnabled as boolean };
}

export function parseFollowPage(value: unknown): FollowPage {
    const v = record(value);
    if (!count(v.page) || !count(v.size) || v.size < 1 || v.size > MAX_SIZE || !flag(v.hasNext) || !count(v.totalCount)
        || !Array.isArray(v.items) || v.items.length > v.size || v.items.length > v.totalCount) throw new Error('팔로우 목록 응답 오류');
    const items = v.items.map(parseFollowUser);
    const tags = items.flatMap(item => item.tag ? [item.tag] : []);
    if (new Set(tags).size !== tags.length) throw new Error('중복 사용자 응답');
    return { items, page: v.page, size: v.size, hasNext: v.hasNext, totalCount: v.totalCount };
}

export function followTab(params: URLSearchParams): FollowListTab {
    return params.get('tab') === 'followers' ? 'followers' : 'following';
}

/** BFF가 받은 목록 조건을 확인한다. 형식이 틀리면 null이다. */
export function followListQuery(params: URLSearchParams): FollowListQuery | null {
    const single = (key: string) => {
        const values = params.getAll(key);
        return values.length > 1 ? null : values[0];
    };
    const number = (key: string, fallback: number) => {
        const raw = single(key);
        if (raw === undefined) return fallback;
        return raw !== null && /^(0|[1-9]\d{0,8})$/.test(raw) ? Number(raw) : NaN;
    };
    const q = single('q'), available = single('available');
    const page = number('page', 0), size = number('size', FOLLOW_PAGE_SIZE);
    if (q === null || (q ?? '').length > FOLLOW_QUERY_MAX) return null;
    if (available === null || available !== undefined && available !== 'true' && available !== 'false') return null;
    if (!count(page) || !count(size) || size < 1 || size > MAX_SIZE) return null;
    return { q: (q ?? '').trim(), available: available === 'true', page, size };
}

export function followListSearch(query: FollowListQuery): string {
    const params = new URLSearchParams();
    if (query.q.trim()) params.set('q', query.q.trim());
    if (query.available) params.set('available', 'true');
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    return params.toString();
}

/** 다음 페이지를 붙일 때 그사이 목록이 바뀌어 다시 온 사람(같은 태그)은 넣지 않는다. */
export function appendFollowUsers(current: FollowUser[], next: FollowUser[]): FollowUser[] {
    const tags = new Set(current.flatMap(item => item.tag ? [item.tag] : []));
    return [...current, ...next.filter(item => !item.tag || !tags.has(item.tag))];
}

export class FollowRequestError extends Error {
    constructor(public readonly status: number, message: string) { super(message); }
}

async function request<T>(path: string, init: RequestInit, parse: (value: unknown) => T, fallback: string): Promise<T> {
    const response = await fetch(path, { cache: 'no-store', ...init });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new FollowRequestError(response.status, typeof body?.message === 'string' ? body.message : fallback);
    }
    return parse(await response.json());
}

const base = (slug: string) => `/api/profiles/${encodeURIComponent(slug)}`;

export function fetchFollowStatus(slug: string, signal?: AbortSignal) {
    return request(`${base(slug)}/follow`, { signal }, parseFollowStatus, '팔로우 정보를 불러오지 못했어요.');
}

export function changeFollow(slug: string, follow: boolean) {
    return request(`${base(slug)}/follow`, { method: follow ? 'POST' : 'DELETE' }, parseFollowResult,
        follow ? '팔로우하지 못했어요. 잠시 후 다시 시도해 주세요.' : '언팔로우하지 못했어요. 잠시 후 다시 시도해 주세요.');
}

export async function fetchFollowList(slug: string, tab: FollowListTab, query: FollowListQuery, signal?: AbortSignal) {
    const page = await request(`${base(slug)}/${tab}?${followListSearch(query)}`, { signal }, parseFollowPage, '목록을 불러오지 못했어요.');
    if (page.page !== query.page || page.size !== query.size) throw new Error('요청한 페이지와 응답이 일치하지 않아요.');
    return page;
}

export function updateFollowNotification(slug: string, enabled: boolean) {
    return request(`${base(slug)}/notify`, { method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ activityNotificationEnabled: enabled }) }, parseFollowNotification, '알림 설정을 저장하지 못했어요.');
}

export function followUserName(user: Pick<FollowUser, 'nickname'>) {
    return user.nickname?.trim() || '이름 없는 사용자';
}
