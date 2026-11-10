import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/notifications/notificationApi', async importOriginal => ({
    ...await importOriginal<typeof import('@/lib/notifications/notificationApi')>(),
    fetchNotifications: vi.fn(),
    changeNotificationRead: vi.fn(),
    readAllNotifications: vi.fn(),
}));
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { NotificationRequestError, changeNotificationRead, fetchNotifications, readAllNotifications as requestReadAll } from '@/lib/notifications/notificationApi';
import { resetLocalStores } from '@/lib/localStore';
import {
    loadMoreNotifications, loadNotifications, openNotification, readAllNotifications, setNotificationRead, startNotificationSync, useNotifications,
} from './notificationStore';
import type { ApiNotification, NotificationPage } from '@/types/notificationApi';

const notice = (id: number, extra: Partial<ApiNotification> = {}): ApiNotification => ({
    id, type: 'PROJECT_STATUS_CHANGED', title: '프로젝트 상태 변경', body: '본문', actionUrl: `/projects/${id}`, projectId: id,
    read: false, resolved: false, review: null, createdAt: '2026-11-10T10:00:00', ...extra,
});
const page = (items: ApiNotification[], extra: Partial<NotificationPage> = {}): NotificationPage =>
    ({ items, page: 0, size: 20, hasNext: false, unreadCount: items.filter(n => !n.read && !n.resolved).length, ...extra });

function current() {
    let value!: ReturnType<typeof useNotifications>;
    function Reader() { value = useNotifications(); return null; }
    renderToStaticMarkup(createElement(Reader));
    return value;
}

let stop: (() => void) | undefined;
let hidden = false;

beforeEach(() => {
    vi.useFakeTimers();
    hidden = false;
    vi.stubGlobal('document', {
        get visibilityState() { return hidden ? 'hidden' : 'visible'; },
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
    });
    vi.mocked(fetchNotifications).mockReset().mockResolvedValue(page([], { size: 1, unreadCount: 4 }));
    vi.mocked(changeNotificationRead).mockReset();
    vi.mocked(requestReadAll).mockReset();
    resetLocalStores();
});
afterEach(() => {
    stop?.();
    stop = undefined;
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

describe('알림 배지 갱신', () => {
    it('시작하면 size=1로 안 읽음 수를 불러오고 60초마다 다시 불러온다', async () => {
        stop = startNotificationSync();
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchNotifications).toHaveBeenCalledWith({ status: 'all', page: 0, size: 1 }, expect.any(AbortSignal));
        expect(current().unreadCount).toBe(4);
        vi.mocked(fetchNotifications).mockResolvedValue(page([], { size: 1, unreadCount: 6 }));
        await vi.advanceTimersByTimeAsync(59_999);
        expect(fetchNotifications).toHaveBeenCalledTimes(1);
        await vi.advanceTimersByTimeAsync(1);
        expect(fetchNotifications).toHaveBeenCalledTimes(2);
        expect(current().unreadCount).toBe(6);
    });
    it('멈추면(로그아웃·토글 ON) 더 조회하지 않고 불러온 알림을 비운다', async () => {
        stop = startNotificationSync();
        await vi.advanceTimersByTimeAsync(0);
        stop();
        stop = undefined;
        expect(current()).toMatchObject({ active: false, unreadCount: 0, list: { items: [], status: 'idle' } });
        await vi.advanceTimersByTimeAsync(180_000);
        expect(fetchNotifications).toHaveBeenCalledTimes(1);
        await loadNotifications('all');
        expect(fetchNotifications).toHaveBeenCalledTimes(1);
    });
    it('숨겨진 탭에서는 조회하지 않는다', async () => {
        hidden = true;
        stop = startNotificationSync();
        await vi.advanceTimersByTimeAsync(120_000);
        expect(fetchNotifications).not.toHaveBeenCalled();
    });
    it('401이면 다시 로그인할 때까지 배지 조회를 멈춘다', async () => {
        vi.mocked(fetchNotifications).mockRejectedValue(new NotificationRequestError(401, '로그인이 필요해요.'));
        stop = startNotificationSync();
        await vi.advanceTimersByTimeAsync(600_000);
        expect(fetchNotifications).toHaveBeenCalledTimes(1);
    });
});

describe('알림 목록', () => {
    it('탭을 열면 첫 페이지를 불러오고 더 보기는 다음 페이지를 뒤에 붙인다', async () => {
        stop = startNotificationSync();
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(fetchNotifications).mockResolvedValueOnce(page([notice(3), notice(2)], { hasNext: true, unreadCount: 3 }));
        await loadNotifications('unread');
        expect(fetchNotifications).toHaveBeenLastCalledWith({ status: 'unread', page: 0, size: 20 });
        expect(current().list).toMatchObject({ tab: 'unread', status: 'ready', page: 0, hasNext: true });
        vi.mocked(fetchNotifications).mockResolvedValueOnce(page([notice(2), notice(1)], { page: 1, unreadCount: 3 }));
        await loadMoreNotifications();
        expect(fetchNotifications).toHaveBeenLastCalledWith({ status: 'unread', page: 1, size: 20 });
        expect(current().list.items.map(n => n.id)).toEqual([3, 2, 1]);
        expect(current().list).toMatchObject({ page: 1, hasNext: false, more: 'idle' });
    });
    it('탭을 빠르게 바꾸면 이전 탭의 늦은 응답을 버린다', async () => {
        stop = startNotificationSync();
        await vi.advanceTimersByTimeAsync(0);
        let resolveOld!: (value: NotificationPage) => void;
        vi.mocked(fetchNotifications).mockReturnValueOnce(new Promise(resolve => { resolveOld = resolve; }));
        const old = loadNotifications('read');
        vi.mocked(fetchNotifications).mockResolvedValueOnce(page([notice(9)]));
        await loadNotifications('all');
        resolveOld(page([notice(1, { read: true })]));
        await old;
        expect(current().list).toMatchObject({ tab: 'all', status: 'ready' });
        expect(current().list.items.map(n => n.id)).toEqual([9]);
    });
    it('불러오기와 더 보기 실패를 안내한다', async () => {
        stop = startNotificationSync();
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(fetchNotifications).mockRejectedValueOnce(new NotificationRequestError(502, '알림 서버에 연결하지 못했어요.'));
        await loadNotifications('all');
        expect(current().list).toMatchObject({ status: 'error', message: '알림 서버에 연결하지 못했어요.' });
        vi.mocked(fetchNotifications).mockResolvedValueOnce(page([notice(2)], { hasNext: true }));
        await loadNotifications('all');
        vi.mocked(fetchNotifications).mockRejectedValueOnce(new Error('network'));
        await loadMoreNotifications();
        expect(current().list).toMatchObject({ status: 'ready', more: 'error', message: '다음 알림을 불러오지 못했어요.' });
        expect(current().list.items).toHaveLength(1);
    });
});

describe('읽음 처리', () => {
    async function ready(items: ApiNotification[]) {
        stop = startNotificationSync();
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(fetchNotifications).mockResolvedValueOnce(page(items));
        await loadNotifications('unread');
    }
    it('읽음·안 읽음을 바꾸면 항목을 빼지 않고 응답의 수로 배지를 맞춘다', async () => {
        await ready([notice(2), notice(1)]);
        vi.mocked(changeNotificationRead).mockResolvedValueOnce({ id: 2, read: true, unreadCount: 1 });
        expect(await setNotificationRead(2, true)).toBe(true);
        expect(changeNotificationRead).toHaveBeenCalledWith(2, true);
        expect(current().unreadCount).toBe(1);
        expect(current().list.items.map(n => [n.id, n.read])).toEqual([[2, true], [1, false]]);
        vi.mocked(changeNotificationRead).mockResolvedValueOnce({ id: 2, read: false, unreadCount: 2 });
        await setNotificationRead(2, false);
        expect(current().list.items[0].read).toBe(false);
        expect(current().unreadCount).toBe(2);
    });
    it('실패하면 상태를 바꾸지 않고 안내한다', async () => {
        await ready([notice(2)]);
        vi.mocked(changeNotificationRead).mockRejectedValueOnce(new NotificationRequestError(404, '알림을 찾을 수 없어요.'));
        expect(await setNotificationRead(2, true)).toBe(false);
        expect(current().list.items[0].read).toBe(false);
        expect(current().list.actionError).toBe('알림을 찾을 수 없어요.');
        expect(current().list.pending).toEqual([]);
    });
    it('모두 읽음은 목록 항목을 모두 읽음으로 바꾼다', async () => {
        await ready([notice(2), notice(1)]);
        vi.mocked(requestReadAll).mockResolvedValueOnce({ unreadCount: 0 });
        expect(await readAllNotifications()).toBe(true);
        expect(current().unreadCount).toBe(0);
        expect(current().list.items.every(n => n.read)).toBe(true);
    });
    it('알림을 누르면 읽음 처리 후 이동하고, 읽음 요청이 실패해도 이동한다', async () => {
        await ready([notice(2), notice(1)]);
        const navigate = vi.fn();
        vi.mocked(changeNotificationRead).mockResolvedValueOnce({ id: 2, read: true, unreadCount: 1 });
        expect(await openNotification(current().list.items[0], navigate)).toBe(true);
        expect(navigate).toHaveBeenLastCalledWith('/projects/2');
        vi.mocked(changeNotificationRead).mockRejectedValueOnce(new Error('network'));
        expect(await openNotification(current().list.items[1], navigate)).toBe(true);
        expect(navigate).toHaveBeenLastCalledWith('/projects/1');
        expect(current().list.actionError).toBeNull();
    });
    it('이미 읽은 알림은 읽음 요청 없이 이동하고, 안전하지 않은 링크는 이동하지 않는다', async () => {
        await ready([notice(2, { read: true }), notice(1, { actionUrl: null })]);
        const navigate = vi.fn();
        await openNotification(current().list.items[0], navigate);
        expect(changeNotificationRead).not.toHaveBeenCalled();
        vi.mocked(changeNotificationRead).mockResolvedValueOnce({ id: 1, read: true, unreadCount: 0 });
        expect(await openNotification(current().list.items[1], navigate)).toBe(false);
        expect(navigate).toHaveBeenCalledTimes(1);
        expect(await openNotification({ ...notice(5), read: true, actionUrl: '//evil.example' }, navigate)).toBe(false);
        expect(navigate).toHaveBeenCalledTimes(1);
    });
});
