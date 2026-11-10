import type {
    ApiNotification, NotificationListQuery, NotificationPage, NotificationReadAllResult, NotificationReadResult,
    NotificationReview, NotificationReviewStatus, NotificationTab,
} from '@/types/notificationApi';

// 알림 API 응답 해석·링크 검사와 브라우저 요청. 브라우저는 BFF(/api/notifications/...)만 부르고 사용자 ID를 보내지 않는다.

export const NOTIFICATION_PAGE_SIZE = 20;
const MAX_SIZE = 100;
const MAX_PATH = 2048;
const TABS: readonly NotificationTab[] = ['all', 'unread', 'read'];
const REVIEW_STATUSES: readonly NotificationReviewStatus[] = ['PENDING', 'COMPLETED', 'EXPIRED'];

const count = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const positive = (v: unknown): v is number => count(v) && v > 0;
const flag = (v: unknown): v is boolean => typeof v === 'boolean';
const text = (v: unknown): v is string => typeof v === 'string';
const record = (v: unknown) => {
    if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('알림 응답 형식 오류');
    return v as Record<string, unknown>;
};

/**
 * 같은 사이트 안의 경로만 허용한다. //host, /\host처럼 다른 사이트로 해석될 수 있는 값, 공백·제어 문자, 외부 주소를 거절한다.
 * 서버도 같은 조건으로 저장하지만 화면은 이동 전에 다시 확인한다.
 */
export function safeNotificationPath(value: unknown): value is string {
    if (typeof value !== 'string' || !value.startsWith('/') || value.length > MAX_PATH) return false;
    if (value[1] === '/' || value[1] === '\\') return false;
    for (const char of value) {
        const code = char.charCodeAt(0);
        if (code <= 0x20 || code === 0x7f || char === '\\') return false;
    }
    return true;
}

function parseReview(value: unknown): NotificationReview | null {
    if (value === null) return null;
    const v = record(value);
    if (!REVIEW_STATUSES.includes(v.status as NotificationReviewStatus) || !text(v.expiresAt)) throw new Error('리뷰 알림 상태 오류');
    return { status: v.status as NotificationReviewStatus, expiresAt: v.expiresAt };
}

export function parseNotification(value: unknown): ApiNotification {
    const v = record(value);
    if (!positive(v.id) || !text(v.type) || !/^[A-Z_]{1,50}$/.test(v.type) || !text(v.title) || !text(v.body)
        || !(v.actionUrl === null || text(v.actionUrl)) || !(v.projectId === null || positive(v.projectId))
        || !flag(v.read) || !flag(v.resolved) || !text(v.createdAt)) throw new Error('알림 항목 오류');
    const review = parseReview(v.review ?? null);
    // 처리 완료는 리뷰 작성 완료·기간 만료에서만 생긴다.
    if (v.resolved !== (review !== null && review.status !== 'PENDING')) throw new Error('알림 처리 상태 오류');
    // 알 수 없는 추가 필드(token 등)는 브라우저 응답에 넣지 않는다.
    return {
        id: v.id, type: v.type, title: v.title, body: v.body,
        actionUrl: safeNotificationPath(v.actionUrl) ? v.actionUrl : null,
        projectId: v.projectId as number | null, read: v.read, resolved: v.resolved, review, createdAt: v.createdAt,
    };
}

export function parseNotificationPage(value: unknown): NotificationPage {
    const v = record(value);
    if (!count(v.page) || !count(v.size) || v.size < 1 || v.size > MAX_SIZE || !flag(v.hasNext) || !count(v.unreadCount)
        || !Array.isArray(v.items) || v.items.length > v.size) throw new Error('알림 목록 응답 오류');
    const items = v.items.map(parseNotification);
    if (new Set(items.map(item => item.id)).size !== items.length) throw new Error('중복 알림 응답');
    return { items, page: v.page, size: v.size, hasNext: v.hasNext, unreadCount: v.unreadCount };
}

export function parseNotificationReadResult(value: unknown): NotificationReadResult {
    const v = record(value);
    if (!positive(v.id) || !flag(v.read) || !count(v.unreadCount)) throw new Error('알림 읽음 응답 오류');
    return { id: v.id, read: v.read, unreadCount: v.unreadCount };
}

export function parseNotificationReadAll(value: unknown): NotificationReadAllResult {
    const v = record(value);
    if (!count(v.unreadCount)) throw new Error('모두 읽음 응답 오류');
    return { unreadCount: v.unreadCount };
}

/** BFF가 받은 목록 조건을 확인한다. 형식이 틀리면 null이다. 같은 이름이 여러 번 오면 거절한다. */
export function notificationListQuery(params: URLSearchParams): NotificationListQuery | null {
    const single = (key: string) => {
        const values = params.getAll(key);
        return values.length > 1 ? null : values[0];
    };
    const number = (key: string, fallback: number) => {
        const raw = single(key);
        if (raw === undefined) return fallback;
        return raw !== null && /^(0|[1-9]\d{0,8})$/.test(raw) ? Number(raw) : NaN;
    };
    const status = single('status');
    if (status === null || status !== undefined && !TABS.includes(status as NotificationTab)) return null;
    const page = number('page', 0), size = number('size', NOTIFICATION_PAGE_SIZE);
    if (!count(page) || !count(size) || size < 1 || size > MAX_SIZE) return null;
    return { status: (status ?? 'all') as NotificationTab, page, size };
}

export function notificationListSearch(query: NotificationListQuery): string {
    return new URLSearchParams({ status: query.status, page: String(query.page), size: String(query.size) }).toString();
}

/** 경로의 알림 ID. 양의 정수만 받는다. */
export function notificationId(raw: string): number | null {
    if (!/^[1-9]\d{0,15}$/.test(raw)) return null;
    const id = Number(raw);
    return Number.isSafeInteger(id) ? id : null;
}

/** 화면에서 읽은 알림으로 보이는지. 처리 완료(리뷰 작성 완료·기간 만료)도 읽은 알림처럼 표시한다(Mock과 같다). */
export function displayedAsRead(notification: Pick<ApiNotification, 'read' | 'resolved'>) {
    return notification.read || notification.resolved;
}

/**
 * 서버 시각(한국 시각, 시간대 표기 없음)을 Mock 화면과 같은 ko-KR 형식으로 보여 준다.
 * 브라우저 시간대와 관계없이 +09:00으로 읽는다. 형식이 다르면 받은 값을 그대로 보여 준다.
 */
export function formatNotificationTime(value: string) {
    const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2})?)/.exec(value);
    const date = match ? new Date(`${match[1]}+09:00`) : null;
    return date && !Number.isNaN(date.getTime()) ? date.toLocaleString('ko-KR', { timeZone: 'Asia/Seoul' }) : value;
}

export function reviewStatusLabel(status: NotificationReviewStatus) {
    return status === 'COMPLETED' ? '작성 완료' : status === 'EXPIRED' ? '기간 만료' : '작성 대기';
}

/** 다음 페이지를 붙일 때 그사이 새 알림이 생겨 다시 온 항목은 넣지 않는다. */
export function appendNotifications(current: ApiNotification[], next: ApiNotification[]): ApiNotification[] {
    const ids = new Set(current.map(item => item.id));
    return [...current, ...next.filter(item => !ids.has(item.id))];
}

export class NotificationRequestError extends Error {
    constructor(public readonly status: number, message: string) { super(message); }
}

async function request<T>(path: string, init: RequestInit, parse: (value: unknown) => T, fallback: string): Promise<T> {
    const response = await fetch(path, { cache: 'no-store', ...init });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new NotificationRequestError(response.status, typeof body?.message === 'string' ? body.message : fallback);
    }
    return parse(await response.json());
}

export async function fetchNotifications(query: NotificationListQuery, signal?: AbortSignal) {
    const page = await request(`/api/notifications?${notificationListSearch(query)}`, { signal }, parseNotificationPage, '알림을 불러오지 못했어요.');
    if (page.page !== query.page || page.size !== query.size) throw new Error('요청한 페이지와 응답이 일치하지 않아요.');
    return page;
}

export async function changeNotificationRead(id: number, read: boolean) {
    const result = await request(`/api/notifications/${id}/read`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ read }),
    }, parseNotificationReadResult, '읽음 상태를 바꾸지 못했어요.');
    if (result.id !== id) throw new Error('요청한 알림과 응답이 일치하지 않아요.');
    return result;
}

export function readAllNotifications() {
    return request('/api/notifications/read-all', { method: 'PATCH' }, parseNotificationReadAll, '모두 읽음 처리하지 못했어요.');
}
