import { createLocalStore, derived, useStore } from '@/lib/localStore';
import { DirectChatRequestError } from '@/lib/messaging/directChatApi';
import { createPoller, documentVisibility } from '@/lib/messaging/directChatPoller';
import type { MessengerSignal } from '@/lib/messaging/messengerSocket';
import {
    fetchProjectChat, fetchProjectChats, fetchProjectUnreadCount, markProjectChatRead, sendProjectMessage,
} from '@/lib/messaging/projectChatApi';
import {
    messengerConnected, onMessengerConnection, onMessengerSessionExpired, onMessengerSignal, sendMessengerTyping,
} from '@/lib/state/messengerSocketStore';
import { notify } from '@/lib/state/prototypeStore';
import type { ProjectChatMember, ProjectChatMessage, ProjectChatStatus, ProjectChatSummary } from '@/types/messaging';

// Mock 토글 OFF의 프로젝트 단톡방 상태. 1:1(directChatStore)과 같은 주기·신호 규칙을 쓰고 localStorage에 저장하지 않는다.
const OPEN_CHAT_INTERVAL = 5000;
const OPEN_CHAT_MAX_INTERVAL = 60000;
const OVERVIEW_INTERVAL = 30000;
const OVERVIEW_MAX_INTERVAL = 120000;
const CONNECTED_INTERVAL = 60000;
const MESSAGE_PAGE_SIZE = 50;
const TYPING_SEND_INTERVAL = 3000;
const TYPING_DISPLAY_TIME = 5000;
/** 닉네임이 없는 팀원의 입력 중 표시 */
export const UNNAMED_TYPIST = '팀원';

export interface ProjectChatListState {
    items: ProjectChatSummary[];
    nextCursor: number | null;
    status: 'loading' | 'ready' | 'error';
    loadingMore: boolean;
}

export interface OpenProjectChat {
    projectId: number;
    status: 'loading' | 'ready' | 'missing' | 'error';
    title: string;
    chatStatus: ProjectChatStatus | null;
    canSend: boolean;
    isHost: boolean;
    members: ProjectChatMember[];
    messages: ProjectChatMessage[];
    nextBefore: number | null;
    loadingOlder: boolean;
    /** 입력 중인 팀원의 닉네임. 그 팀원의 메시지가 오거나 5초가 지나면 뺀다. */
    typing: string[];
}

export interface ProjectChatState {
    list: ProjectChatListState;
    unread: number;
    open: OpenProjectChat | null;
    sessionExpired: boolean;
}

const initialState = (): ProjectChatState => ({
    list: { items: [], nextCursor: null, status: 'loading', loadingMore: false },
    unread: 0,
    open: null,
    sessionExpired: false,
});

const store = createLocalStore(initialState);
const state = () => store.get();
const update = (change: (current: ProjectChatState) => ProjectChatState) => store.set(change(state()));
const updateOpen = (projectId: number, change: (open: OpenProjectChat) => OpenProjectChat) => update(current =>
    current.open?.projectId === projectId ? { ...current, open: change(current.open) } : current);

let query = '';
let listVersion = 0;
let listWatchers = 0;
let atBottom = true;
let markedMessageId = 0;
let marking = false;
let sending = false;
let realtime = false;
let typingSent: { projectId: number; at: number } | null = null;
const typingTimers = new Map<string, ReturnType<typeof setTimeout>>();

const failedWith = (error: unknown, status: number) => error instanceof DirectChatRequestError && error.status === status;
const typistName = (nickname: string | null) => nickname || UNNAMED_TYPIST;

/** 폴링과 전송 응답이 겹쳐도 메시지가 한 번만 남도록 ID로 합치고 오름차순으로 둔다. */
function mergeMessages(current: ProjectChatMessage[], incoming: ProjectChatMessage[]) {
    if (!incoming.length) return current;
    const byId = new Map(current.map(message => [message.id, message]));
    for (const message of incoming) byId.set(message.id, message);
    return [...byId.values()].sort((a, b) => a.id - b.id);
}

function stopTyping(name?: string) {
    const names = name === undefined ? [...typingTimers.keys()] : [name];
    for (const typist of names) {
        clearTimeout(typingTimers.get(typist));
        typingTimers.delete(typist);
    }
    const open = state().open;
    if (open && open.typing.some(typist => names.includes(typist))) {
        updateOpen(open.projectId, current => ({ ...current, typing: current.typing.filter(typist => !names.includes(typist)) }));
    }
}

function expireSession() {
    overview.stop();
    openChat.stop();
    stopTyping();
    update(current => ({ ...current, sessionExpired: true }));
}

async function refreshOverview(signal: AbortSignal) {
    const version = listVersion;
    const searching = query !== '';
    const [unread, page] = await Promise.all([
        fetchProjectUnreadCount(signal),
        listWatchers > 0 ? fetchProjectChats(searching ? { q: query } : {}, signal) : null,
    ]);
    update(current => {
        if (!page || version !== listVersion) return { ...current, unread: unread.count };
        // 첫 페이지를 다시 읽은 결과에, 더 불러온 이전 프로젝트(ID가 더 작은 것)를 이어 붙인다.
        const ids = new Set(page.items.map(item => item.id));
        const floor = page.items.at(-1)?.id ?? 0;
        const older = page.nextCursor === null ? [] : current.list.items.filter(item => !ids.has(item.id) && item.id < floor);
        return {
            ...current,
            unread: unread.count,
            list: { ...current.list, items: [...page.items, ...older], nextCursor: older.length ? current.list.nextCursor : page.nextCursor, status: 'ready' },
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
    const projectId = state().open?.projectId;
    if (projectId === undefined) return;
    const firstLoad = state().open?.status === 'loading';
    const known = state().open?.messages.at(-1)?.id ?? 0;
    const arrived: ProjectChatMessage[] = [];
    for (;;) {
        const after = state().open?.messages.at(-1)?.id;
        const detail = await fetchProjectChat(projectId, after ? { after, size: MESSAGE_PAGE_SIZE } : { size: MESSAGE_PAGE_SIZE }, signal);
        if (state().open?.projectId !== projectId) return;
        arrived.push(...detail.messages.filter(message => message.id > known));
        updateOpen(projectId, open => ({
            ...open,
            status: 'ready',
            title: detail.title,
            chatStatus: detail.status,
            canSend: detail.canSend,
            isHost: detail.isHost,
            members: detail.members,
            messages: mergeMessages(open.messages, detail.messages),
            // after 응답의 nextBefore는 항상 비어 있으므로 처음 읽을 때의 값을 유지한다.
            nextBefore: after ? open.nextBefore : detail.nextBefore,
        }));
        if (!after || detail.messages.length < MESSAGE_PAGE_SIZE) break;
    }
    if (!firstLoad) {
        // 입력 중이던 팀원의 메시지가 도착하면 그 팀원의 표시만 지운다.
        for (const message of arrived) if (!message.mine) stopTyping(typistName(message.sender.nickname));
        if (arrived.length) overview.poke({ evenIfHidden: true });
    }
    markReadIfSeen();
}

const openChat = createPoller({
    interval: () => (realtime ? CONNECTED_INTERVAL : OPEN_CHAT_INTERVAL),
    maxInterval: OPEN_CHAT_MAX_INTERVAL,
    run: pollOpenChat,
    onFailure: error => {
        if (failedWith(error, 401)) return expireSession();
        const projectId = state().open?.projectId;
        if (projectId === undefined) return;
        if (failedWith(error, 404)) {
            openChat.stop();
            updateOpen(projectId, open => ({ ...open, status: 'missing' }));
        } else updateOpen(projectId, open => open.status === 'loading' ? { ...open, status: 'error' } : open);
    },
});

/** 화면에 보인 메시지까지 읽음으로 남긴다. 탭이 숨겨졌거나 위로 올려 읽는 중이면 미룬다. */
function markReadIfSeen() {
    const open = state().open;
    if (!open || open.status !== 'ready' || !atBottom || marking || !documentVisibility.isVisible()) return;
    const lastFromOthers = open.messages.findLast(message => !message.mine)?.id ?? null;
    const tail = open.messages.at(-1)?.id;
    if (lastFromOthers === null || tail === undefined || lastFromOthers <= markedMessageId) return;
    marking = true;
    void markProjectChatRead(open.projectId, tail).then(() => {
        markedMessageId = tail;
        update(current => ({
            ...current,
            list: { ...current.list, items: current.list.items.map(item => item.id === open.projectId ? { ...item, unreadCount: 0 } : item) },
        }));
        overview.poke();
    }).catch(error => {
        if (failedWith(error, 401)) expireSession();
        if (failedWith(error, 404)) updateOpen(open.projectId, current => ({ ...current, status: 'missing' }));
        // 다른 실패는 다음 조회 때 다시 시도한다.
    }).finally(() => {
        marking = false;
    });
}

/**
 * 신호는 조회할 때를 알려 줄 뿐이고 본문은 항상 서버에서 다시 읽는다. 숨겨진 탭도 신호를 처리하고 읽음 처리만 미룬다.
 * 읽음 신호는 태그로 팀원을 찾을 수 있으면 화면에서 바로 옮기고, 찾지 못하면 다시 조회한다.
 */
function handleSignal(signal: MessengerSignal) {
    if (signal.roomType !== 'project') return;
    const open = state().open;
    const isOpen = open?.projectId === signal.roomId;
    if (signal.type === 'message.created') {
        if (isOpen) openChat.poke({ evenIfHidden: true });
        else overview.poke({ evenIfHidden: true });
    } else if (signal.type === 'message.read') {
        const reader = isOpen && signal.memberTag ? open.members.find(member => member.tag === signal.memberTag) : undefined;
        if (reader) {
            updateOpen(signal.roomId, current => ({
                ...current,
                members: current.members.map(member => member.id === reader.id
                    ? { ...member, lastReadMessageId: Math.max(member.lastReadMessageId ?? 0, signal.lastReadMessageId) } : member),
            }));
        } else if (isOpen) openChat.poke({ evenIfHidden: true });
        // 내 다른 탭에서 읽었으면 안 읽은 수가 바뀐다. 다른 팀원의 읽음이면 바뀌지 않으므로 다시 읽지 않는다.
        if (!reader || reader.me) overview.poke({ evenIfHidden: true });
    } else if (signal.type === 'project.members.changed') {
        if(signal.status==='PASSED')notify('강퇴 투표가 가결되어 프로젝트 참여 및 채팅 멤버가 변경됐어요.');
        overview.poke({ evenIfHidden: true });
        if (isOpen) openChat.poke({ evenIfHidden: true });
    } else if (isOpen) {
        const name = typistName(signal.nickname);
        clearTimeout(typingTimers.get(name));
        typingTimers.set(name, setTimeout(() => stopTyping(name), TYPING_DISPLAY_TIME));
        if (!open.typing.includes(name)) updateOpen(signal.roomId, current => ({ ...current, typing: [...current.typing, name] }));
    }
}

/** 연결되면 놓친 메시지를 바로 조회하고 폴링을 늦춘다. 끊기면 기본 주기로 되돌린다. */
function handleConnection(connected: boolean) {
    realtime = connected;
    if (connected) {
        openChat.poke({ evenIfHidden: true });
        overview.poke({ evenIfHidden: true });
    } else {
        stopTyping();
        openChat.reschedule();
        overview.reschedule();
    }
}

/** 로그인 회원이 토글 OFF일 때 배지(7.21)를 조회한다. 정리 함수는 불러온 서버 데이터를 비운다. */
export function startProjectChatSync() {
    realtime = messengerConnected();
    const unsubscribe = [onMessengerSignal(handleSignal), onMessengerConnection(handleConnection), onMessengerSessionExpired(expireSession)];
    overview.start();
    return () => {
        unsubscribe.forEach(stop => stop());
        overview.stop();
        openChat.stop();
        stopTyping();
        typingSent = null;
        realtime = false;
        query = '';
        listVersion += 1;
        atBottom = true;
        markedMessageId = 0;
        store.set(initialState());
    };
}

/** 사이드바의 프로젝트 탭이 보이는 동안만 목록도 함께 조회한다. */
export function watchProjectChatList() {
    listWatchers += 1;
    overview.poke();
    return () => {
        listWatchers -= 1;
    };
}

export function setProjectChatQuery(next: string) {
    const trimmed = next.trim();
    if (trimmed === query) return;
    query = trimmed;
    listVersion += 1;
    update(current => ({ ...current, list: { ...current.list, nextCursor: null, items: [], status: 'loading' } }));
    overview.poke();
}

export async function loadMoreProjectChats() {
    const { nextCursor, loadingMore } = state().list;
    if (nextCursor === null || loadingMore) return;
    const version = listVersion;
    update(current => ({ ...current, list: { ...current.list, loadingMore: true } }));
    try {
        const page = await fetchProjectChats({ ...(query ? { q: query } : {}), cursor: nextCursor });
        if (version !== listVersion) return;
        update(current => {
            const ids = new Set(current.list.items.map(item => item.id));
            return { ...current, list: { ...current.list, items: [...current.list.items, ...page.items.filter(item => !ids.has(item.id))], nextCursor: page.nextCursor } };
        });
    } catch (error) {
        if (failedWith(error, 401)) expireSession();
        else notify('프로젝트 채팅 목록을 더 불러오지 못했어요.');
    } finally {
        update(current => ({ ...current, list: { ...current.list, loadingMore: false } }));
    }
}

/** 프로젝트 채팅을 열고 새 메시지를 조회한다. 첫 접근이면 서버가 방을 만든다. 정리 함수는 폴링을 멈춘다. */
export function openProjectChat(projectId: number) {
    openChat.stop();
    stopTyping();
    atBottom = true;
    markedMessageId = 0;
    update(current => ({
        ...current,
        open: {
            projectId, status: 'loading', title: '', chatStatus: null, canSend: false, isHost: false, members: [], messages: [],
            nextBefore: null, loadingOlder: false, typing: [],
        },
    }));
    if (!state().sessionExpired) openChat.start();
    return () => {
        if (state().open?.projectId !== projectId) return;
        openChat.stop();
        stopTyping();
        update(current => ({ ...current, open: null }));
    };
}

export async function loadOlderProjectMessages() {
    const open = state().open;
    if (!open || open.nextBefore === null || open.loadingOlder) return;
    const { projectId, nextBefore } = open;
    updateOpen(projectId, current => ({ ...current, loadingOlder: true }));
    try {
        const detail = await fetchProjectChat(projectId, { before: nextBefore });
        updateOpen(projectId, current => ({ ...current, messages: mergeMessages(current.messages, detail.messages), nextBefore: detail.nextBefore }));
    } catch (error) {
        if (failedWith(error, 401)) expireSession();
        else notify('이전 메시지를 불러오지 못했어요.');
    } finally {
        updateOpen(projectId, current => ({ ...current, loadingOlder: false }));
    }
}

export function setProjectChatAtBottom(value: boolean) {
    atBottom = value;
    markReadIfSeen();
}

export async function sendOpenProjectMessage(body: string): Promise<{ ok: boolean; error?: string }> {
    const open = state().open;
    if (!open) return { ok: false, error: '채팅방을 찾을 수 없어요.' };
    if (sending) return { ok: false, error: '메시지를 보내는 중이에요.' };
    sending = true;
    try {
        const message = await sendProjectMessage(open.projectId, body);
        // 서버가 보낸 사람의 읽음 위치도 옮기므로 이 메시지까지 읽음으로 본다.
        markedMessageId = Math.max(markedMessageId, message.id);
        updateOpen(open.projectId, current => ({ ...current, messages: mergeMessages(current.messages, [message]) }));
        overview.poke();
        return { ok: true };
    } catch (error) {
        if (failedWith(error, 401)) expireSession();
        if (failedWith(error, 404)) updateOpen(open.projectId, current => ({ ...current, status: 'missing' }));
        if (failedWith(error, 409)) openChat.poke();
        return { ok: false, error: error instanceof DirectChatRequestError ? error.message : '메시지를 보내지 못했습니다.' };
    } finally {
        sending = false;
    }
}

/** 입력창이 바뀌면 부른다. 같은 방에는 3초에 한 번만 보내고, 보낼 수 없는 방과 연결이 없을 때는 보내지 않는다. */
export function notifyProjectChatTyping(now = Date.now()) {
    const open = state().open;
    if (!open || open.status !== 'ready' || !open.canSend || !realtime) return;
    if (typingSent?.projectId === open.projectId && now - typingSent.at < TYPING_SEND_INTERVAL) return;
    if (sendMessengerTyping('project', open.projectId)) typingSent = { projectId: open.projectId, at: now };
}

const projectChats = derived([store], state);

export function useProjectChats() {
    return useStore(projectChats);
}
