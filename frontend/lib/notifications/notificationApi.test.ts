import { describe, expect, it } from 'vitest';
import {
    appendNotifications, displayedAsRead, notificationId, notificationListQuery, notificationListSearch, parseNotification,
    parseNotificationPage, parseNotificationReadAll, parseNotificationReadResult, reviewStatusLabel, safeNotificationPath,
} from './notificationApi';

const item = (extra: Record<string, unknown> = {}) => ({
    id: 31, type: 'PROJECT_STATUS_CHANGED', title: '프로젝트 상태 변경', body: '알림 프로젝트: 모집 완료', actionUrl: '/projects/12',
    projectId: 12, read: false, resolved: false, review: null, createdAt: '2026-11-10T10:00:00', ...extra,
});
const page = (items: unknown[], extra: Record<string, unknown> = {}) => ({ items, page: 0, size: 20, hasNext: false, unreadCount: 3, ...extra });

describe('알림 응답 해석', () => {
    it('필요한 필드만 남기고 token 같은 추가 필드는 버린다', () => {
        const parsed = parseNotification(item({ token: 'secret', reviewId: 4, recipientId: 1 }));
        expect(parsed).toEqual(item());
        expect(parsed).not.toHaveProperty('token');
    });
    it('리뷰 상태와 처리 완료가 맞아야 한다', () => {
        const review = { status: 'COMPLETED', expiresAt: '2026-12-06T12:00:00' };
        expect(parseNotification(item({ type: 'PROJECT_REVIEW_REQUESTED', review, resolved: true })).review).toEqual(review);
        expect(parseNotification(item({ review: { status: 'PENDING', expiresAt: '2026-12-06T12:00:00' } })).resolved).toBe(false);
        expect(() => parseNotification(item({ review, resolved: false }))).toThrow();
        expect(() => parseNotification(item({ resolved: true }))).toThrow();
        expect(() => parseNotification(item({ review: { status: 'DONE', expiresAt: 'x' }, resolved: true }))).toThrow();
    });
    it.each(['//evil.example', '/\\evil.example', 'https://evil.example/projects/1', 'javascript:alert(1)', '/projects/1 x', 'projects/1', ''])(
        '내부 경로가 아닌 링크 %s는 null로 바꿔 이동하지 않게 한다', actionUrl => {
            expect(parseNotification(item({ actionUrl })).actionUrl).toBeNull();
            expect(safeNotificationPath(actionUrl)).toBe(false);
        });
    it('내부 경로는 그대로 둔다', () => {
        for (const path of ['/projects/12', '/projects/12/reviews', '/notifications?tab=unread']) expect(safeNotificationPath(path)).toBe(true);
        expect(safeNotificationPath(null)).toBe(false);
        expect(safeNotificationPath('/' + 'a'.repeat(2048))).toBe(false);
    });
    it.each([
        ['id', 0], ['id', 1.5], ['type', 'lower_case'], ['type', ''], ['title', null], ['body', 3], ['actionUrl', 7], ['projectId', 0],
        ['read', 'false'], ['resolved', null], ['createdAt', 1],
    ])('%s가 %s이면 거절한다', (key, value) => {
        expect(() => parseNotification(item({ [key]: value }))).toThrow();
    });
    it('목록 크기·중복·수를 확인한다', () => {
        expect(parseNotificationPage(page([item()])).unreadCount).toBe(3);
        expect(() => parseNotificationPage(page([item(), item()]))).toThrow();
        expect(() => parseNotificationPage(page([item()], { size: 0 }))).toThrow();
        expect(() => parseNotificationPage(page([item()], { size: 101 }))).toThrow();
        expect(() => parseNotificationPage(page([item(), item({ id: 2 })], { size: 1 }))).toThrow();
        expect(() => parseNotificationPage(page([], { unreadCount: -1 }))).toThrow();
        expect(() => parseNotificationPage(page([], { hasNext: 'no' }))).toThrow();
        expect(() => parseNotificationPage([])).toThrow();
    });
    it('읽음·모두 읽음 응답을 확인한다', () => {
        expect(parseNotificationReadResult({ id: 3, read: true, unreadCount: 0, extra: 1 })).toEqual({ id: 3, read: true, unreadCount: 0 });
        expect(() => parseNotificationReadResult({ id: 3, read: 'yes', unreadCount: 0 })).toThrow();
        expect(parseNotificationReadAll({ unreadCount: 0 })).toEqual({ unreadCount: 0 });
        expect(() => parseNotificationReadAll({ unreadCount: '0' })).toThrow();
    });
});

describe('알림 목록 조건', () => {
    it('기본값과 허용 값만 받는다', () => {
        expect(notificationListQuery(new URLSearchParams())).toEqual({ status: 'all', page: 0, size: 20 });
        expect(notificationListQuery(new URLSearchParams('status=unread&page=2&size=1'))).toEqual({ status: 'unread', page: 2, size: 1 });
        for (const bad of ['status=READ', 'status=bad', 'status=read&status=all', 'page=-1', 'page=01', 'page=a', 'size=0', 'size=101', 'size=1.5'])
            expect(notificationListQuery(new URLSearchParams(bad))).toBeNull();
        expect(notificationListSearch({ status: 'read', page: 1, size: 20 })).toBe('status=read&page=1&size=20');
    });
    it('알림 ID는 양의 정수만 받는다', () => {
        expect(notificationId('31')).toBe(31);
        for (const bad of ['0', '-1', '01', '1.5', 'abc', '', '1e3', '99999999999999999']) expect(notificationId(bad)).toBeNull();
    });
});

describe('알림 화면 값', () => {
    it('처리 완료는 읽은 알림처럼 보이고 리뷰 상태 문구를 정한다', () => {
        expect(displayedAsRead({ read: false, resolved: true })).toBe(true);
        expect(displayedAsRead({ read: false, resolved: false })).toBe(false);
        expect([reviewStatusLabel('PENDING'), reviewStatusLabel('COMPLETED'), reviewStatusLabel('EXPIRED')]).toEqual(['작성 대기', '작성 완료', '기간 만료']);
    });
    it('다음 페이지를 붙일 때 이미 있는 알림은 다시 넣지 않는다', () => {
        const first = [parseNotification(item({ id: 3 })), parseNotification(item({ id: 2 }))];
        const next = [parseNotification(item({ id: 2 })), parseNotification(item({ id: 1 }))];
        expect(appendNotifications(first, next).map(n => n.id)).toEqual([3, 2, 1]);
    });
});
