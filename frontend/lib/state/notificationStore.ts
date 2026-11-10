import { createLocalStore, derived, useStore } from '@/lib/localStore';
import { createPoller } from '@/lib/messaging/directChatPoller';
import {
    NOTIFICATION_PAGE_SIZE, NotificationRequestError, appendNotifications, changeNotificationRead, fetchNotifications,
    readAllNotifications as requestReadAll, safeNotificationPath,
} from '@/lib/notifications/notificationApi';
import type { ApiNotification, NotificationTab } from '@/types/notificationApi';

// Mock 토글 OFF의 서버 알림 상태(docs/notification-plan.md 6.2). 원본은 서버이므로 localStorage에 저장하지 않는다.
// 로그인 회원이 토글 OFF일 때 NotificationSync가 시작하고, 로그아웃·토글 ON·계정 변경 시 멈추고 비운다.
// 헤더 배지는 60초마다(Mock 동기화 주기와 같다) size=1로 불러 unreadCount만 쓴다.
const BADGE_INTERVAL = 60000;
const BADGE_MAX_INTERVAL = 300000;

export interface NotificationListState {
    tab: NotificationTab;
    status: 'idle' | 'loading' | 'ready' | 'error';
    items: ApiNotification[];
    page: number;
    hasNext: boolean;
    more: 'idle' | 'loading' | 'error';
    /** 처음 불러오기 또는 더 보기 실패 안내 */
    message: string | null;
    /** 읽음 변경 요청 중인 알림 ID */
    pending: number[];
    /** 마지막 읽음 변경 실패 안내 */
    actionError: string | null;
}

export interface NotificationState {
    active: boolean;
    unreadCount: number;
    list: NotificationListState;
}

const emptyList = (tab: NotificationTab = 'all'): NotificationListState => ({
    tab, status: 'idle', items: [], page: 0, hasNext: false, more: 'idle', message: null, pending: [], actionError: null,
});
const initialState = (): NotificationState => ({ active: false, unreadCount: 0, list: emptyList() });

const store = createLocalStore(initialState);
const state = () => store.get();
const update = (change: (current: NotificationState) => NotificationState) => store.set(change(state()));
const updateList = (change: (list: NotificationListState) => NotificationListState) => update(current => ({ ...current, list: change(current.list) }));
const messageOf = (error: unknown, fallback: string) => error instanceof NotificationRequestError ? error.message : fallback;
const unauthorized = (error: unknown) => error instanceof NotificationRequestError && error.status === 401;

// 탭을 바꾸거나 멈추면 올라간다. 그 전에 보낸 목록 요청의 응답은 버린다.
let listVersion = 0;

async function refreshBadge(signal: AbortSignal) {
    const page = await fetchNotifications({ status: 'all', page: 0, size: 1 }, signal);
    if (state().active) update(current => ({ ...current, unreadCount: page.unreadCount }));
}

const badge = createPoller({
    interval: BADGE_INTERVAL,
    maxInterval: BADGE_MAX_INTERVAL,
    run: refreshBadge,
    // 로그인이 끝났으면 다시 로그인할 때까지 조회하지 않는다.
    onFailure: error => { if (unauthorized(error)) badge.stop(); },
});

/** 배지 조회를 시작한다. 정리 함수는 조회를 멈추고 불러온 서버 알림을 비운다. */
export function startNotificationSync() {
    listVersion += 1;
    store.set({ ...initialState(), active: true });
    badge.start();
    return () => {
        badge.stop();
        listVersion += 1;
        store.set(initialState());
    };
}

/** 모달·페이지를 열 때와 탭을 바꿀 때 첫 페이지를 서버 기준으로 다시 불러온다. */
export async function loadNotifications(tab: NotificationTab = state().list.tab) {
    if (!state().active) return;
    const version = ++listVersion;
    updateList(() => ({ ...emptyList(tab), status: 'loading' }));
    try {
        const page = await fetchNotifications({ status: tab, page: 0, size: NOTIFICATION_PAGE_SIZE });
        if (version !== listVersion) return;
        update(current => ({
            ...current, unreadCount: page.unreadCount,
            list: { ...current.list, status: 'ready', items: page.items, page: 0, hasNext: page.hasNext },
        }));
    } catch (error) {
        if (version !== listVersion) return;
        updateList(list => ({ ...list, status: 'error', message: messageOf(error, '알림을 불러오지 못했어요.') }));
    }
}

/** 다음 페이지(page + 1)를 뒤에 붙인다. */
export async function loadMoreNotifications() {
    const list = state().list;
    if (!state().active || list.status !== 'ready' || !list.hasNext || list.more === 'loading') return;
    const version = listVersion;
    const next = list.page + 1;
    updateList(current => ({ ...current, more: 'loading', message: null }));
    try {
        const page = await fetchNotifications({ status: list.tab, page: next, size: NOTIFICATION_PAGE_SIZE });
        if (version !== listVersion) return;
        update(current => ({
            ...current, unreadCount: page.unreadCount,
            list: { ...current.list, items: appendNotifications(current.list.items, page.items), page: next, hasNext: page.hasNext, more: 'idle' },
        }));
    } catch (error) {
        if (version !== listVersion) return;
        updateList(current => ({ ...current, more: 'error', message: messageOf(error, '다음 알림을 불러오지 못했어요.') }));
    }
}

/**
 * 읽음·안 읽음을 바꾸고 응답의 unreadCount로 배지를 바로 맞춘다.
 * 다음 페이지를 붙인 목록에서 탭 조건과 맞지 않게 되어도 항목을 빼지 않는다. 탭을 다시 열면 서버 기준으로 다시 불러온다.
 */
export async function setNotificationRead(id: number, read: boolean, options: { quiet?: boolean } = {}) {
    if (!state().active || state().list.pending.includes(id)) return false;
    updateList(list => ({ ...list, pending: [...list.pending, id], actionError: null }));
    try {
        const result = await changeNotificationRead(id, read);
        update(current => ({
            ...current, unreadCount: result.unreadCount,
            list: { ...current.list, items: current.list.items.map(item => item.id === id ? { ...item, read: result.read } : item) },
        }));
        return true;
    } catch (error) {
        if (!options.quiet) updateList(list => ({ ...list, actionError: messageOf(error, '읽음 상태를 바꾸지 못했어요.') }));
        return false;
    } finally {
        updateList(list => ({ ...list, pending: list.pending.filter(value => value !== id) }));
    }
}

export async function readAllNotifications() {
    if (!state().active) return false;
    updateList(list => ({ ...list, actionError: null }));
    try {
        const result = await requestReadAll();
        update(current => ({
            ...current, unreadCount: result.unreadCount,
            list: { ...current.list, items: current.list.items.map(item => ({ ...item, read: true })) },
        }));
        return true;
    } catch (error) {
        updateList(list => ({ ...list, actionError: messageOf(error, '모두 읽음 처리하지 못했어요.') }));
        return false;
    }
}

/**
 * 알림을 누르면 읽음 처리를 요청한 뒤 actionUrl로 이동한다. 읽음 요청이 실패해도 이동하며 다음 갱신 때 서버 상태로 맞춘다.
 * actionUrl이 내부 경로 조건을 통과하지 못하면 이동하지 않고 false다.
 */
export async function openNotification(notification: ApiNotification, navigate: (href: string) => void) {
    if (!notification.read) await setNotificationRead(notification.id, true, { quiet: true });
    if (!safeNotificationPath(notification.actionUrl)) return false;
    navigate(notification.actionUrl);
    return true;
}

const notifications = derived([store], () => state());

export function useNotifications() {
    return useStore(notifications);
}
