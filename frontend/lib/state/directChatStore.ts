import { createLocalStore, derived, useStore } from '@/lib/localStore';
import {
    DirectChatRequestError, fetchDirectChat, fetchDirectChats, fetchDirectUnreadCount, markDirectChatRead,
    reportUser, sendDirectMessage, startDirectChat,
} from '@/lib/messaging/directChatApi';
import { createPoller, documentVisibility } from '@/lib/messaging/directChatPoller';
import { directChatHref, lastPeerMessageId, mergeDirectMessages, mergeFirstChatPage } from '@/lib/messaging/directChatView';
import type { MessengerSignal } from '@/lib/messaging/messengerSocket';
import {
    messengerConnected, onMessengerConnection, onMessengerSessionExpired, onMessengerSignal, sendMessengerTyping,
} from '@/lib/state/messengerSocketStore';
import { notify } from '@/lib/state/prototypeStore';
import type { DirectChatMessage, DirectChatPeer, DirectChatSummary } from '@/types/messaging';

// Mock 토글 OFF의 1:1 메신저 상태. 원본은 서버이므로 localStorage에 저장하지 않는다.
// WebSocket이 연결된 동안에는 신호로 바로 조회하고, 폴링은 놓친 신호를 메우는 보조 수단으로 길게 둔다.
const OPEN_CHAT_INTERVAL = 5000;
const OPEN_CHAT_MAX_INTERVAL = 60000;
const OVERVIEW_INTERVAL = 30000;
const OVERVIEW_MAX_INTERVAL = 120000;
const CONNECTED_INTERVAL = 60000;
const MESSAGE_PAGE_SIZE = 50;
const TYPING_SEND_INTERVAL = 3000;
const TYPING_DISPLAY_TIME = 5000;

export interface DirectChatListState {
    items: DirectChatSummary[];
    nextCursor: number | null;
    status: 'loading' | 'ready' | 'error';
    loadingMore: boolean;
}

export interface OpenDirectChat {
    id: number;
    status: 'loading' | 'ready' | 'missing' | 'error';
    peer: DirectChatPeer | null;
    canSend: boolean;
    peerLastReadMessageId: number | null;
    messages: DirectChatMessage[];
    nextBefore: number | null;
    loadingOlder: boolean;
    /** 상대가 입력 중이다. 상대 메시지가 도착하거나 5초가 지나면 지운다. */
    peerTyping: boolean;
}

export interface DirectChatState {
    list: DirectChatListState;
    /** 검색하지 않은 목록의 가장 최근 대화. 주소에 대화가 없을 때 이 대화를 연다. */
    latestChatId: number | null;
    unread: number;
    open: OpenDirectChat | null;
    sessionExpired: boolean;
}

export type DirectChatResult = { ok: boolean; error?: string };

const initialState = (): DirectChatState => ({
    list: { items: [], nextCursor: null, status: 'loading', loadingMore: false },
    latestChatId: null,
    unread: 0,
    open: null,
    sessionExpired: false,
});

const store = createLocalStore(initialState);
const state = () => store.get();
const update = (change: (current: DirectChatState) => DirectChatState) => store.set(change(state()));
const updateOpen = (id: number, change: (open: OpenDirectChat) => OpenDirectChat) => update(current =>
    current.open?.id === id ? { ...current, open: change(current.open) } : current);

let query = '';
let listVersion = 0;
let listWatchers = 0;
let atBottom = true;
let markedMessageId = 0;
let marking = false;
let sending = false;
let realtime = false;
let typingTimer: ReturnType<typeof setTimeout> | undefined;
let typingSent: { roomId: number; at: number } | null = null;

const failedWith = (error: unknown, status: number) => error instanceof DirectChatRequestError && error.status === status;

function clearPeerTyping() {
    clearTimeout(typingTimer);
    typingTimer = undefined;
    const open = state().open;
    if (open?.peerTyping) updateOpen(open.id, current => ({ ...current, peerTyping: false }));
}

function expireSession() {
    overview.stop();
    openChat.stop();
    clearPeerTyping();
    update(current => ({ ...current, sessionExpired: true }));
}

async function refreshOverview(signal: AbortSignal) {
    const version = listVersion;
    const searching = query !== '';
    const [unread, page] = await Promise.all([
        fetchDirectUnreadCount(signal),
        listWatchers > 0 ? fetchDirectChats(searching ? { q: query } : {}, signal) : null,
    ]);
    update(current => {
        if (!page || version !== listVersion) return { ...current, unread: unread.count };
        return {
            ...current,
            unread: unread.count,
            latestChatId: searching ? current.latestChatId : page.items[0]?.id ?? null,
            list: { ...current.list, ...mergeFirstChatPage(current.list, page), status: 'ready' },
        };
    });
}

const overview = createPoller({
    interval: () => (realtime ? CONNECTED_INTERVAL : OVERVIEW_INTERVAL),
    maxInterval: OVERVIEW_MAX_INTERVAL,
    run: refreshOverview,
    onFailure: error => {
        if (failedWith(error, 401)) return expireSession();
        update(current => current.list.status === 'loading' ? { ...current, list: { ...current.list, status: 'error' } } : current);
    },
});

async function pollOpenChat(signal: AbortSignal) {
    const id = state().open?.id;
    if (id === undefined) return;
    const firstLoad = state().open?.status === 'loading';
    const known = state().open?.messages.at(-1)?.id ?? 0;
    let received = false;
    let fromPeer = false;
    for (;;) {
        const after = state().open?.messages.at(-1)?.id;
        const detail = await fetchDirectChat(id, after ? { after, size: MESSAGE_PAGE_SIZE } : { size: MESSAGE_PAGE_SIZE }, signal);
        if (state().open?.id !== id) return;
        updateOpen(id, open => ({
            ...open,
            status: 'ready',
            peer: detail.peer,
            canSend: detail.canSend,
            peerLastReadMessageId: detail.peerLastReadMessageId,
            messages: mergeDirectMessages(open.messages, detail.messages),
            // after 응답의 nextBefore는 항상 비어 있으므로 처음 읽을 때의 값을 유지한다.
            nextBefore: after ? open.nextBefore : detail.nextBefore,
        }));
        received = received || (state().open?.messages.at(-1)?.id ?? 0) > known;
        fromPeer = fromPeer || detail.messages.some(message => !message.mine && message.id > known);
        if (!after || detail.messages.length < MESSAGE_PAGE_SIZE) break;
    }
    if (fromPeer && !firstLoad) clearPeerTyping();
    // 신호로 조회했으면 숨겨진 탭이어도 배지를 함께 갱신한다.
    if (received && !firstLoad) overview.poke({ evenIfHidden: true });
    markReadIfSeen();
}

const openChat = createPoller({
    interval: () => (realtime ? CONNECTED_INTERVAL : OPEN_CHAT_INTERVAL),
    maxInterval: OPEN_CHAT_MAX_INTERVAL,
    run: pollOpenChat,
    onFailure: error => {
        if (failedWith(error, 401)) return expireSession();
        const id = state().open?.id;
        if (id === undefined) return;
        if (failedWith(error, 404)) {
            openChat.stop();
            updateOpen(id, open => ({ ...open, status: 'missing' }));
        } else updateOpen(id, open => open.status === 'loading' ? { ...open, status: 'error' } : open);
    },
});

/** 화면에 보인 메시지까지 읽음으로 남긴다. 탭이 숨겨졌거나 위로 올려 읽는 중이면 미룬다. */
function markReadIfSeen() {
    const open = state().open;
    if (!open || open.status !== 'ready' || !atBottom || marking || !documentVisibility.isVisible()) return;
    const lastPeer = lastPeerMessageId(open.messages);
    const tail = open.messages.at(-1)?.id;
    if (lastPeer === null || tail === undefined || lastPeer <= markedMessageId) return;
    marking = true;
    void markDirectChatRead(open.id, tail).then(() => {
        markedMessageId = tail;
        update(current => ({
            ...current,
            list: { ...current.list, items: current.list.items.map(item => item.id === open.id ? { ...item, unreadCount: 0 } : item) },
        }));
        overview.poke();
    }).catch(error => {
        if (failedWith(error, 401)) expireSession();
        // 다른 실패는 다음 폴링 때 다시 시도한다.
    }).finally(() => {
        marking = false;
    });
}

/**
 * 신호는 조회할 때를 알려 줄 뿐이다. 본문과 읽음 위치는 항상 서버에서 다시 읽으므로 신호가 중복되거나 유실돼도 결과가 같다.
 * 숨겨진 탭도 신호를 처리하고, 읽음 처리만 탭이 보일 때까지 미룬다.
 */
function handleSignal(signal: MessengerSignal) {
    // 프로젝트 채팅은 14단계에서 같은 연결로 처리한다.
    if (signal.roomType !== 'direct') return;
    const open = state().open;
    const isOpen = open?.id === signal.roomId;
    if (signal.type === 'message.created') {
        if (isOpen) openChat.poke({ evenIfHidden: true });
        else overview.poke({ evenIfHidden: true });
    } else if (signal.type === 'message.read') {
        // 내가 다른 탭에서 읽어 온 신호여도, 옮겨진 위치의 메시지는 상대가 보낸 것이라 상대 위치로 써도 틀리지 않는다.
        if (isOpen) updateOpen(signal.roomId, current => ({
            ...current,
            peerLastReadMessageId: Math.max(current.peerLastReadMessageId ?? 0, signal.lastReadMessageId),
        }));
        overview.poke({ evenIfHidden: true });
    } else if (isOpen) {
        clearTimeout(typingTimer);
        updateOpen(signal.roomId, current => ({ ...current, peerTyping: true }));
        typingTimer = setTimeout(clearPeerTyping, TYPING_DISPLAY_TIME);
    }
}

/** 연결되면 놓친 메시지를 바로 조회하고 폴링을 늦춘다. 끊기면 6단계의 주기로 되돌린다. */
function handleConnection(connected: boolean) {
    realtime = connected;
    if (connected) {
        openChat.poke({ evenIfHidden: true });
        overview.poke({ evenIfHidden: true });
    } else {
        clearPeerTyping();
        openChat.reschedule();
        overview.reschedule();
    }
}

/** 로그인 회원이 토글 OFF일 때 배지·목록 폴링을 시작한다. 정리 함수는 불러온 서버 데이터를 비운다. */
export function startDirectChatSync() {
    realtime = messengerConnected();
    const unsubscribe = [onMessengerSignal(handleSignal), onMessengerConnection(handleConnection), onMessengerSessionExpired(expireSession)];
    overview.start();
    return () => {
        unsubscribe.forEach(stop => stop());
        overview.stop();
        openChat.stop();
        clearTimeout(typingTimer);
        typingTimer = undefined;
        typingSent = null;
        realtime = false;
        query = '';
        listVersion += 1;
        atBottom = true;
        markedMessageId = 0;
        store.set(initialState());
    };
}

/** 사이드바가 보이는 동안만 목록도 함께 조회한다. */
export function watchDirectChatList() {
    listWatchers += 1;
    overview.poke();
    return () => {
        listWatchers -= 1;
    };
}

export function setDirectChatQuery(next: string) {
    const trimmed = next.trim();
    if (trimmed === query) return;
    query = trimmed;
    listVersion += 1;
    update(current => ({ ...current, list: { ...current.list, nextCursor: null, items: [], status: 'loading' } }));
    overview.poke();
}

export async function loadMoreDirectChats() {
    const { nextCursor, loadingMore } = state().list;
    if (nextCursor === null || loadingMore) return;
    const version = listVersion;
    update(current => ({ ...current, list: { ...current.list, loadingMore: true } }));
    try {
        const page = await fetchDirectChats({ ...(query ? { q: query } : {}), cursor: nextCursor });
        if (version !== listVersion) return;
        update(current => {
            const ids = new Set(current.list.items.map(item => item.id));
            return { ...current, list: { ...current.list, items: [...current.list.items, ...page.items.filter(item => !ids.has(item.id))], nextCursor: page.nextCursor } };
        });
    } catch (error) {
        if (failedWith(error, 401)) expireSession();
        else notify('대화 목록을 더 불러오지 못했어요.');
    } finally {
        update(current => ({ ...current, list: { ...current.list, loadingMore: false } }));
    }
}

/** 대화를 열고 5초마다 새 메시지를 조회한다. 정리 함수는 폴링을 멈춘다. */
export function openDirectChat(id: number) {
    openChat.stop();
    clearTimeout(typingTimer);
    typingTimer = undefined;
    atBottom = true;
    markedMessageId = 0;
    update(current => ({
        ...current,
        open: {
            id, status: 'loading', peer: null, canSend: false, peerLastReadMessageId: null, messages: [], nextBefore: null,
            loadingOlder: false, peerTyping: false,
        },
    }));
    if (!state().sessionExpired) openChat.start();
    return () => {
        if (state().open?.id !== id) return;
        openChat.stop();
        clearTimeout(typingTimer);
        typingTimer = undefined;
        update(current => ({ ...current, open: null }));
    };
}

/**
 * 입력창이 바뀌면 부른다. 같은 대화에는 3초에 한 번만 보내고, 보낼 수 없는 대화와 연결이 없을 때는 보내지 않는다.
 * 서버도 회원·방당 1초에 한 번만 받는다.
 */
export function notifyDirectChatTyping(now = Date.now()) {
    const open = state().open;
    if (!open || open.status !== 'ready' || !open.canSend || !realtime) return;
    if (typingSent?.roomId === open.id && now - typingSent.at < TYPING_SEND_INTERVAL) return;
    if (sendMessengerTyping('direct', open.id)) typingSent = { roomId: open.id, at: now };
}

export async function loadOlderDirectMessages() {
    const open = state().open;
    if (!open || open.nextBefore === null || open.loadingOlder) return;
    const { id, nextBefore } = open;
    updateOpen(id, current => ({ ...current, loadingOlder: true }));
    try {
        const detail = await fetchDirectChat(id, { before: nextBefore });
        updateOpen(id, current => ({ ...current, messages: mergeDirectMessages(current.messages, detail.messages), nextBefore: detail.nextBefore }));
    } catch (error) {
        if (failedWith(error, 401)) expireSession();
        else notify('이전 메시지를 불러오지 못했어요.');
    } finally {
        updateOpen(id, current => ({ ...current, loadingOlder: false }));
    }
}

export function setDirectChatAtBottom(value: boolean) {
    atBottom = value;
    markReadIfSeen();
}

export async function sendOpenDirectMessage(body: string): Promise<DirectChatResult> {
    const open = state().open;
    if (!open) return { ok: false, error: '대화를 찾을 수 없어요.' };
    if (sending) return { ok: false, error: '메시지를 보내는 중이에요.' };
    sending = true;
    try {
        const message = await sendDirectMessage(open.id, body);
        // 서버가 보낸 사람의 읽음 위치도 옮기므로 이 메시지까지 읽음으로 본다.
        markedMessageId = Math.max(markedMessageId, message.id);
        updateOpen(open.id, current => ({ ...current, messages: mergeDirectMessages(current.messages, [message]) }));
        overview.poke();
        return { ok: true };
    } catch (error) {
        if (failedWith(error, 401)) expireSession();
        if (failedWith(error, 404)) updateOpen(open.id, current => ({ ...current, status: 'missing' }));
        if (failedWith(error, 409)) openChat.poke();
        return { ok: false, error: error instanceof DirectChatRequestError ? error.message : '메시지를 보내지 못했습니다.' };
    } finally {
        sending = false;
    }
}

/** 프로필의 메시지 보내기. 비로그인과 만료는 로그인으로 보내고, 그 밖의 실패는 안내 문구를 돌려준다. */
export async function startChatWithMember(slug: string, loggedIn: boolean, navigate: (url: string) => void): Promise<DirectChatResult> {
    const login = () => navigate(`/login?next=${encodeURIComponent(`/users/${slug}`)}`);
    if (!loggedIn) {
        login();
        return { ok: true };
    }
    try {
        const { id } = await startDirectChat(slug);
        navigate(directChatHref(id));
        return { ok: true };
    } catch (error) {
        if (failedWith(error, 401)) {
            login();
            return { ok: true };
        }
        return { ok: false, error: error instanceof DirectChatRequestError ? error.message : '대화를 시작하지 못했어요.' };
    }
}

export async function submitUserReport(slug: string, reason: string): Promise<DirectChatResult> {
    try {
        await reportUser(slug, reason);
        notify('사용자 신고를 접수했어요.');
        return { ok: true };
    } catch (error) {
        if (failedWith(error, 401)) expireSession();
        return { ok: false, error: error instanceof DirectChatRequestError ? error.message : '신고하지 못했어요.' };
    }
}

const directChats = derived([store], state);

export function useDirectChats() {
    return useStore(directChats);
}
