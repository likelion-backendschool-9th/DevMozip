import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/messaging/directChatApi', async importOriginal => ({
    ...await importOriginal<typeof import('@/lib/messaging/directChatApi')>(),
    fetchDirectChat: vi.fn(),
    fetchDirectChats: vi.fn(),
    fetchDirectUnreadCount: vi.fn(),
    markDirectChatRead: vi.fn(),
    reportUser: vi.fn(),
    sendDirectMessage: vi.fn(),
    startDirectChat: vi.fn(),
}));
vi.mock('@/lib/state/messengerSocketStore', async () => (await import('@/tests/helpers/messengerSocketMock')).messengerSocketStoreMock());
import {
    DirectChatRequestError, fetchDirectChat, fetchDirectChats, fetchDirectUnreadCount, markDirectChatRead, reportUser,
    sendDirectMessage, startDirectChat,
} from '@/lib/messaging/directChatApi';
import { resetLocalStores } from '@/lib/localStore';
import {
    loadMoreDirectChats, loadOlderDirectMessages, notifyDirectChatTyping, openDirectChat, sendOpenDirectMessage, setDirectChatAtBottom,
    setDirectChatQuery, startChatWithMember, startDirectChatSync, submitUserReport, useDirectChats, watchDirectChatList,
} from './directChatStore';
import {
    emitConnection, emitSessionExpired, emitSignal, resetSocketMock, socketMock, subscriberCount,
} from '@/tests/helpers/messengerSocketMock';
import { directChatDetailFixture, directChatListFixture, directChatMessageFixture } from '@/tests/helpers/directChatFixture';
import type { DirectChatDetail, DirectChatMessage, DirectChatSummary } from '@/types/messaging';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';

const message = (id: number, mine = false): DirectChatMessage => ({ ...directChatMessageFixture, id, mine, body: `메시지 ${id}` });
const detail = (messages: DirectChatMessage[], extra: Partial<DirectChatDetail> = {}): DirectChatDetail => ({ ...directChatDetailFixture, peerLastReadMessageId: null, messages, ...extra });
const summary = (id: number, lastMessageId: number, unreadCount = 0): DirectChatSummary => ({ ...directChatListFixture.items[0], id, unreadCount, lastMessage: message(lastMessageId) });
const failure = (status: number, text = '실패') => new DirectChatRequestError(status, text);

/** 렌더링 없이 저장소의 현재 값을 읽는다. */
function current() {
    let value!: ReturnType<typeof useDirectChats>;
    function Reader() { value = useDirectChats(); return null; }
    renderToStaticMarkup(createElement(Reader));
    return value;
}

let stop: (() => void) | undefined;
let closeChat: (() => void) | undefined;
let hidden = false;

beforeEach(() => {
    vi.useFakeTimers();
    hidden = false;
    vi.stubGlobal('document', {
        get visibilityState() { return hidden ? 'hidden' : 'visible'; },
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
    });
    vi.mocked(fetchDirectChat).mockReset().mockResolvedValue(detail([]));
    vi.mocked(fetchDirectChats).mockReset().mockResolvedValue({ items: [], nextCursor: null });
    vi.mocked(fetchDirectUnreadCount).mockReset().mockResolvedValue({ count: 0 });
    vi.mocked(markDirectChatRead).mockReset().mockResolvedValue(undefined);
    vi.mocked(reportUser).mockReset().mockResolvedValue(undefined);
    vi.mocked(sendDirectMessage).mockReset();
    vi.mocked(startDirectChat).mockReset();
    resetLocalStores();
    resetSocketMock();
});
afterEach(() => {
    closeChat?.();
    closeChat = undefined;
    stop?.();
    stop = undefined;
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

async function openChat(id = 12) {
    closeChat = openDirectChat(id);
    await vi.advanceTimersByTimeAsync(0);
}

describe('배지와 목록', () => {
    it('시작하면 안 읽은 수를 읽고 30초마다 다시 읽는다', async () => {
        vi.mocked(fetchDirectUnreadCount).mockResolvedValue({ count: 3 });
        stop = startDirectChatSync();
        await vi.advanceTimersByTimeAsync(0);
        expect(current().unread).toBe(3);
        expect(fetchDirectUnreadCount).toHaveBeenCalledTimes(1);
        expect(fetchDirectChats).not.toHaveBeenCalled();
        await vi.advanceTimersByTimeAsync(30000);
        expect(fetchDirectUnreadCount).toHaveBeenCalledTimes(2);
    });

    it('사이드바가 보이는 동안만 목록을 조회하고 최근 대화를 기억한다', async () => {
        vi.mocked(fetchDirectChats).mockResolvedValue(directChatListFixture);
        stop = startDirectChatSync();
        const unwatch = watchDirectChatList();
        await vi.advanceTimersByTimeAsync(0);
        expect(current().list).toMatchObject({ status: 'ready', items: directChatListFixture.items });
        expect(current().latestChatId).toBe(12);
        const calls = vi.mocked(fetchDirectChats).mock.calls.length;
        unwatch();
        await vi.advanceTimersByTimeAsync(30000);
        expect(fetchDirectChats).toHaveBeenCalledTimes(calls);
    });

    it('검색은 q로 조회하고 최근 대화를 바꾸지 않으며 늦게 온 이전 결과를 버린다', async () => {
        vi.mocked(fetchDirectChats).mockResolvedValue({ items: [summary(1, 10)], nextCursor: null });
        stop = startDirectChatSync();
        const unwatch = watchDirectChatList();
        await vi.advanceTimersByTimeAsync(0);
        expect(current().latestChatId).toBe(1);

        let resolveOld!: (value: { items: DirectChatSummary[]; nextCursor: null }) => void;
        vi.mocked(fetchDirectChats).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
        await vi.advanceTimersByTimeAsync(30000);
        vi.mocked(fetchDirectChats).mockResolvedValue({ items: [summary(2, 20)], nextCursor: null });
        setDirectChatQuery(' 샘플 ');
        resolveOld({ items: [summary(9, 90)], nextCursor: null });
        await vi.advanceTimersByTimeAsync(0);
        expect(vi.mocked(fetchDirectChats).mock.lastCall?.[0]).toEqual({ q: '샘플' });
        expect(current().list.items.map(item => item.id)).toEqual([2]);
        expect(current().latestChatId).toBe(1);
        unwatch();
    });

    it('목록을 이어서 불러온다', async () => {
        vi.mocked(fetchDirectChats).mockResolvedValue({ items: [summary(2, 20)], nextCursor: 20 });
        stop = startDirectChatSync();
        const unwatch = watchDirectChatList();
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(fetchDirectChats).mockResolvedValue({ items: [summary(1, 10)], nextCursor: null });
        await loadMoreDirectChats();
        expect(fetchDirectChats).toHaveBeenLastCalledWith({ cursor: 20 });
        expect(current().list.items.map(item => item.id)).toEqual([2, 1]);
        expect(current().list.nextCursor).toBeNull();
        expect(current().list.loadingMore).toBe(false);
        unwatch();
    });

    it('목록을 처음 읽지 못하면 오류 상태가 되고 간격을 늘려 다시 시도한다', async () => {
        vi.mocked(fetchDirectChats).mockRejectedValue(new Error('연결 실패'));
        stop = startDirectChatSync();
        const unwatch = watchDirectChatList();
        await vi.advanceTimersByTimeAsync(0);
        expect(current().list.status).toBe('error');
        const calls = vi.mocked(fetchDirectChats).mock.calls.length;
        await vi.advanceTimersByTimeAsync(30000);
        expect(fetchDirectChats).toHaveBeenCalledTimes(calls);
        vi.mocked(fetchDirectChats).mockResolvedValue(directChatListFixture);
        await vi.advanceTimersByTimeAsync(90000);
        expect(current().list.status).toBe('ready');
        unwatch();
    });

    it('401이면 모든 폴링을 멈추고 로그인 안내 상태가 된다', async () => {
        vi.mocked(fetchDirectUnreadCount).mockRejectedValue(failure(401));
        stop = startDirectChatSync();
        await openChat();
        await vi.advanceTimersByTimeAsync(0);
        expect(current().sessionExpired).toBe(true);
        const unreadCalls = vi.mocked(fetchDirectUnreadCount).mock.calls.length;
        const chatCalls = vi.mocked(fetchDirectChat).mock.calls.length;
        await vi.advanceTimersByTimeAsync(300000);
        expect(fetchDirectUnreadCount).toHaveBeenCalledTimes(unreadCalls);
        expect(fetchDirectChat).toHaveBeenCalledTimes(chatCalls);
    });

    it('정리하면 불러온 서버 데이터를 비운다', async () => {
        vi.mocked(fetchDirectUnreadCount).mockResolvedValue({ count: 3 });
        vi.mocked(fetchDirectChats).mockResolvedValue(directChatListFixture);
        vi.mocked(fetchDirectChat).mockResolvedValue(detail([message(1)]));
        const cleanup = startDirectChatSync();
        const unwatch = watchDirectChatList();
        await openChat();
        await vi.advanceTimersByTimeAsync(0);
        expect(current().open?.messages).toHaveLength(1);
        cleanup();
        unwatch();
        closeChat = undefined;
        expect(current()).toMatchObject({ unread: 0, open: null, latestChatId: null, sessionExpired: false });
        expect(current().list.items).toEqual([]);
        const calls = vi.mocked(fetchDirectUnreadCount).mock.calls.length;
        await vi.advanceTimersByTimeAsync(60000);
        expect(fetchDirectUnreadCount).toHaveBeenCalledTimes(calls);
    });
});

describe('열린 대화', () => {
    it('처음에는 최신 메시지를 읽고, 이후 5초마다 after로 새 메시지만 읽어 병합한다', async () => {
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1), message(2)], { nextBefore: 1 }));
        await openChat();
        expect(fetchDirectChat).toHaveBeenLastCalledWith(12, { size: 50 }, expect.any(AbortSignal));
        expect(current().open).toMatchObject({ status: 'ready', nextBefore: 1, canSend: true });
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(2), message(3)], { nextBefore: null, peerLastReadMessageId: 2 }));
        await vi.advanceTimersByTimeAsync(5000);
        expect(fetchDirectChat).toHaveBeenLastCalledWith(12, { after: 2, size: 50 }, expect.any(AbortSignal));
        expect(current().open?.messages.map(item => item.id)).toEqual([1, 2, 3]);
        expect(current().open).toMatchObject({ nextBefore: 1, peerLastReadMessageId: 2 });
    });

    it('after 결과가 size개이면 끝까지 이어서 읽는다', async () => {
        const firstPage = Array.from({ length: 50 }, (_, index) => message(index + 2));
        vi.mocked(fetchDirectChat)
            .mockResolvedValueOnce(detail([message(1)]))
            .mockResolvedValueOnce(detail(firstPage))
            .mockResolvedValueOnce(detail([message(52)]));
        await openChat();
        await vi.advanceTimersByTimeAsync(5000);
        expect(vi.mocked(fetchDirectChat).mock.calls.slice(1).map(call => call[1])).toEqual([{ after: 1, size: 50 }, { after: 51, size: 50 }]);
        expect(current().open?.messages).toHaveLength(52);
    });

    it('새 메시지가 오면 목록과 배지를 바로 갱신한다', async () => {
        stop = startDirectChatSync();
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1, true)]));
        await openChat();
        await vi.advanceTimersByTimeAsync(0);
        const calls = vi.mocked(fetchDirectUnreadCount).mock.calls.length;
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([]));
        await vi.advanceTimersByTimeAsync(5000);
        expect(fetchDirectUnreadCount).toHaveBeenCalledTimes(calls);
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(2, true)]));
        await vi.advanceTimersByTimeAsync(5000);
        expect(fetchDirectUnreadCount).toHaveBeenCalledTimes(calls + 1);
    });

    it('없는 대화는 missing으로 표시하고 조회를 멈춘다', async () => {
        vi.mocked(fetchDirectChat).mockRejectedValue(failure(404));
        await openChat();
        expect(current().open?.status).toBe('missing');
        await vi.advanceTimersByTimeAsync(60000);
        expect(fetchDirectChat).toHaveBeenCalledTimes(1);
    });

    it('처음 읽지 못하면 오류 상태지만, 읽은 뒤의 일시 실패는 내용을 유지한다', async () => {
        vi.mocked(fetchDirectChat).mockRejectedValueOnce(new Error('연결 실패'));
        await openChat();
        expect(current().open?.status).toBe('error');
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1)]));
        await vi.advanceTimersByTimeAsync(10000);
        expect(current().open?.status).toBe('ready');
        vi.mocked(fetchDirectChat).mockRejectedValueOnce(new Error('연결 실패'));
        await vi.advanceTimersByTimeAsync(5000);
        expect(current().open).toMatchObject({ status: 'ready' });
        expect(current().open?.messages).toHaveLength(1);
    });

    it('다른 대화로 바꾸면 늦게 온 이전 대화 응답을 버린다', async () => {
        let resolveOld!: (value: DirectChatDetail) => void;
        vi.mocked(fetchDirectChat).mockImplementationOnce(() => new Promise(resolve => { resolveOld = resolve; }));
        closeChat = openDirectChat(1);
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(20)], { id: 2 }));
        closeChat = openDirectChat(2);
        await vi.advanceTimersByTimeAsync(0);
        resolveOld(detail([message(99)], { id: 1 }));
        await vi.advanceTimersByTimeAsync(0);
        expect(current().open?.id).toBe(2);
        expect(current().open?.messages.map(item => item.id)).toEqual([20]);
    });

    it('이전 메시지를 앞에 붙이고 다음 커서로 바꾼다', async () => {
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(5)], { nextBefore: 5 }));
        await openChat();
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(3), message(4)], { nextBefore: null }));
        await loadOlderDirectMessages();
        expect(fetchDirectChat).toHaveBeenLastCalledWith(12, { before: 5 });
        expect(current().open?.messages.map(item => item.id)).toEqual([3, 4, 5]);
        expect(current().open).toMatchObject({ nextBefore: null, loadingOlder: false });
    });
});

describe('읽음 처리', () => {
    it('화면 맨 아래에서 상대 메시지를 보면 마지막 메시지까지 한 번만 읽음 처리한다', async () => {
        stop = startDirectChatSync();
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1), message(2)]));
        await openChat();
        await vi.advanceTimersByTimeAsync(0);
        expect(markDirectChatRead).toHaveBeenCalledTimes(1);
        expect(markDirectChatRead).toHaveBeenCalledWith(12, 2);
        await vi.advanceTimersByTimeAsync(5000);
        expect(markDirectChatRead).toHaveBeenCalledTimes(1);
    });

    it('읽음 처리 뒤 목록의 안 읽은 수를 지우고 배지를 다시 읽는다', async () => {
        vi.mocked(fetchDirectChats).mockResolvedValue({ items: [summary(12, 2, 2), summary(13, 1, 4)], nextCursor: null });
        stop = startDirectChatSync();
        const unwatch = watchDirectChatList();
        await vi.advanceTimersByTimeAsync(0);
        vi.mocked(fetchDirectChats).mockResolvedValue({ items: [summary(12, 2, 0), summary(13, 1, 4)], nextCursor: null });
        vi.mocked(fetchDirectUnreadCount).mockResolvedValue({ count: 4 });
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(2)]));
        await openChat();
        await vi.advanceTimersByTimeAsync(0);
        expect(current().unread).toBe(4);
        expect(current().list.items.map(item => item.unreadCount)).toEqual([0, 4]);
        unwatch();
    });

    it('내 메시지만 있으면 호출하지 않는다', async () => {
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1, true)]));
        await openChat();
        expect(markDirectChatRead).not.toHaveBeenCalled();
    });

    it('위로 올려 읽는 중에는 미루고 맨 아래로 돌아오면 호출한다', async () => {
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1, true)]));
        await openChat();
        setDirectChatAtBottom(false);
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(2)]));
        await vi.advanceTimersByTimeAsync(5000);
        expect(markDirectChatRead).not.toHaveBeenCalled();
        setDirectChatAtBottom(true);
        expect(markDirectChatRead).toHaveBeenCalledWith(12, 2);
    });

    it('탭이 숨겨진 동안에는 호출하지 않는다', async () => {
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1, true)]));
        await openChat();
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(2)]));
        await vi.advanceTimersByTimeAsync(5000);
        expect(markDirectChatRead).toHaveBeenCalledTimes(1);
        vi.mocked(markDirectChatRead).mockClear();
        hidden = true;
        setDirectChatAtBottom(false);
        setDirectChatAtBottom(true);
        expect(markDirectChatRead).not.toHaveBeenCalled();
    });

    it('실패하면 다음 조회 때 다시 시도한다', async () => {
        vi.mocked(markDirectChatRead).mockRejectedValueOnce(new Error('연결 실패'));
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1)]));
        await openChat();
        await vi.advanceTimersByTimeAsync(5000);
        expect(markDirectChatRead).toHaveBeenCalledTimes(2);
    });
});

describe('전송', () => {
    it('응답을 병합해 폴링과 겹쳐도 한 번만 남고, 내 전송은 읽음 처리를 다시 부르지 않는다', async () => {
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1)]));
        await openChat();
        vi.mocked(markDirectChatRead).mockClear();
        vi.mocked(sendDirectMessage).mockResolvedValue(message(2, true));
        expect(await sendOpenDirectMessage('안녕')).toEqual({ ok: true });
        expect(sendDirectMessage).toHaveBeenCalledWith(12, '안녕');
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(2, true)]));
        await vi.advanceTimersByTimeAsync(5000);
        expect(current().open?.messages.map(item => item.id)).toEqual([1, 2]);
        expect(markDirectChatRead).not.toHaveBeenCalled();
    });

    it('대기 중에는 중복 전송을 막는다', async () => {
        await openChat();
        let finish!: (value: DirectChatMessage) => void;
        vi.mocked(sendDirectMessage).mockImplementation(() => new Promise(resolve => { finish = resolve; }));
        const first = sendOpenDirectMessage('안녕');
        expect((await sendOpenDirectMessage('안녕')).ok).toBe(false);
        expect(sendDirectMessage).toHaveBeenCalledTimes(1);
        finish(message(1, true));
        expect((await first).ok).toBe(true);
    });

    it('상대 정지 409는 안내 문구를 돌려주고 전송 가능 여부를 다시 읽는다', async () => {
        await openChat();
        vi.mocked(sendDirectMessage).mockRejectedValue(failure(409, '상대가 이용 정지 상태라 메시지를 보낼 수 없어요.'));
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([], { canSend: false }));
        expect(await sendOpenDirectMessage('안녕')).toEqual({ ok: false, error: '상대가 이용 정지 상태라 메시지를 보낼 수 없어요.' });
        await vi.advanceTimersByTimeAsync(0);
        expect(current().open?.canSend).toBe(false);
    });

    it('연결 오류는 일반 안내를 돌려주고 401은 만료 상태가 된다', async () => {
        await openChat();
        vi.mocked(sendDirectMessage).mockRejectedValueOnce(new Error('http://internal'));
        expect(await sendOpenDirectMessage('안녕')).toEqual({ ok: false, error: '메시지를 보내지 못했습니다.' });
        vi.mocked(sendDirectMessage).mockRejectedValueOnce(failure(401, '로그인이 만료됐어요.'));
        expect((await sendOpenDirectMessage('안녕')).ok).toBe(false);
        expect(current().sessionExpired).toBe(true);
    });

    it('대화가 사라졌으면 missing으로 바꾼다', async () => {
        await openChat();
        vi.mocked(sendDirectMessage).mockRejectedValue(failure(404, '대화를 찾을 수 없어요.'));
        await sendOpenDirectMessage('안녕');
        expect(current().open?.status).toBe('missing');
    });
});

describe('실시간 신호', () => {
    const created = (roomId: number, messageId: number) => ({ type: 'message.created' as const, roomType: 'direct' as const, roomId, messageId });
    const read = (roomId: number, lastReadMessageId: number) => ({ type: 'message.read' as const, roomType: 'direct' as const, roomId, lastReadMessageId, memberTag: null });
    const typing = (roomId: number) => ({ type: 'typing' as const, roomType: 'direct' as const, roomId, nickname: null });

    it('연결되면 놓친 메시지를 바로 조회하고, 연결된 동안에는 60초마다만 조회한다', async () => {
        stop = startDirectChatSync();
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1)]));
        await openChat();
        const unreadCalls = vi.mocked(fetchDirectUnreadCount).mock.calls.length;
        emitConnection(true);
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchDirectChat).toHaveBeenLastCalledWith(12, { after: 1, size: 50 }, expect.any(AbortSignal));
        expect(fetchDirectUnreadCount).toHaveBeenCalledTimes(unreadCalls + 1);
        const chatCalls = vi.mocked(fetchDirectChat).mock.calls.length;
        await vi.advanceTimersByTimeAsync(59999);
        expect(fetchDirectChat).toHaveBeenCalledTimes(chatCalls);
        expect(fetchDirectUnreadCount).toHaveBeenCalledTimes(unreadCalls + 1);
        await vi.advanceTimersByTimeAsync(1);
        expect(fetchDirectChat).toHaveBeenCalledTimes(chatCalls + 1);
        expect(fetchDirectUnreadCount).toHaveBeenCalledTimes(unreadCalls + 2);
    });

    it('끊기면 바로 6단계 주기(열린 대화 5초)로 되돌린다', async () => {
        socketMock.connected = true;
        stop = startDirectChatSync();
        await openChat();
        const calls = vi.mocked(fetchDirectChat).mock.calls.length;
        await vi.advanceTimersByTimeAsync(10000);
        expect(fetchDirectChat).toHaveBeenCalledTimes(calls);
        emitConnection(false);
        await vi.advanceTimersByTimeAsync(5000);
        expect(fetchDirectChat).toHaveBeenCalledTimes(calls + 1);
    });

    it('새 메시지 신호는 열린 대화면 after로 조회하고, 다른 대화면 목록과 배지를 다시 읽는다', async () => {
        socketMock.connected = true;
        stop = startDirectChatSync();
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1)]));
        await openChat();
        await vi.advanceTimersByTimeAsync(0);
        const unreadCalls = vi.mocked(fetchDirectUnreadCount).mock.calls.length;
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(2)]));
        emitSignal(created(12, 2));
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchDirectChat).toHaveBeenLastCalledWith(12, { after: 1, size: 50 }, expect.any(AbortSignal));
        expect(current().open?.messages.map(item => item.id)).toEqual([1, 2]);
        const chatCalls = vi.mocked(fetchDirectChat).mock.calls.length;
        const afterOpen = vi.mocked(fetchDirectUnreadCount).mock.calls.length;
        expect(afterOpen).toBeGreaterThan(unreadCalls);
        emitSignal(created(99, 5));
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchDirectChat).toHaveBeenCalledTimes(chatCalls);
        expect(fetchDirectUnreadCount).toHaveBeenCalledTimes(afterOpen + 1);
    });

    it('짧은 시간에 몰린 신호는 요청 중이면 한 번으로 묶는다', async () => {
        socketMock.connected = true;
        stop = startDirectChatSync();
        await openChat();
        let finish!: (value: DirectChatDetail) => void;
        vi.mocked(fetchDirectChat).mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
        const calls = vi.mocked(fetchDirectChat).mock.calls.length;
        for (let id = 1; id <= 5; id++) emitSignal(created(12, id));
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchDirectChat).toHaveBeenCalledTimes(calls + 1);
        finish(detail([message(1)]));
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchDirectChat).toHaveBeenCalledTimes(calls + 2);
    });

    it('숨겨진 탭도 신호로 조회하지만 읽음 처리는 탭이 보일 때까지 미룬다', async () => {
        socketMock.connected = true;
        stop = startDirectChatSync();
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1, true)]));
        await openChat();
        hidden = true;
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(2)]));
        emitSignal(created(12, 2));
        await vi.advanceTimersByTimeAsync(0);
        expect(current().open?.messages.map(item => item.id)).toEqual([1, 2]);
        expect(markDirectChatRead).not.toHaveBeenCalled();
        hidden = false;
        setDirectChatAtBottom(true);
        expect(markDirectChatRead).toHaveBeenCalledWith(12, 2);
    });

    it('읽음 신호는 상대 읽음 위치를 앞으로만 옮기고 안 읽은 수를 다시 읽는다', async () => {
        stop = startDirectChatSync();
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(5, true), message(8, true)], { peerLastReadMessageId: 5 }));
        await openChat();
        await vi.advanceTimersByTimeAsync(0);
        const unreadCalls = vi.mocked(fetchDirectUnreadCount).mock.calls.length;
        emitSignal(read(12, 3));
        expect(current().open?.peerLastReadMessageId).toBe(5);
        emitSignal(read(12, 8));
        expect(current().open?.peerLastReadMessageId).toBe(8);
        emitSignal(read(99, 100));
        expect(current().open?.peerLastReadMessageId).toBe(8);
        await vi.advanceTimersByTimeAsync(0);
        expect(vi.mocked(fetchDirectUnreadCount).mock.calls.length).toBeGreaterThan(unreadCalls);
    });

    it('입력 중 신호는 열린 대화에만 표시하고, 5초가 지나거나 상대 메시지가 오면 지운다', async () => {
        socketMock.connected = true;
        stop = startDirectChatSync();
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(1)]));
        await openChat();
        emitSignal(typing(99));
        expect(current().open?.peerTyping).toBe(false);
        emitSignal(typing(12));
        expect(current().open?.peerTyping).toBe(true);
        await vi.advanceTimersByTimeAsync(4999);
        emitSignal(typing(12));
        await vi.advanceTimersByTimeAsync(4999);
        expect(current().open?.peerTyping).toBe(true);
        await vi.advanceTimersByTimeAsync(1);
        expect(current().open?.peerTyping).toBe(false);

        emitSignal(typing(12));
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(2, true)]));
        emitSignal(created(12, 2));
        await vi.advanceTimersByTimeAsync(0);
        expect(current().open?.peerTyping).toBe(true);
        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([message(3)]));
        emitSignal(created(12, 3));
        await vi.advanceTimersByTimeAsync(0);
        expect(current().open?.peerTyping).toBe(false);
    });

    it('연결이 끊기거나 다른 대화를 열면 입력 중 표시를 지운다', async () => {
        socketMock.connected = true;
        stop = startDirectChatSync();
        await openChat();
        emitSignal(typing(12));
        emitConnection(false);
        expect(current().open?.peerTyping).toBe(false);
        emitConnection(true);
        emitSignal(typing(12));
        closeChat = openDirectChat(13);
        expect(current().open).toMatchObject({ id: 13, peerTyping: false });
    });

    it('입력 중 신호는 연결돼 있고 보낼 수 있는 열린 대화에서만 3초에 한 번 보낸다', async () => {
        stop = startDirectChatSync();
        await openChat();
        notifyDirectChatTyping(1000);
        expect(socketMock.typingSent).toEqual([]);
        emitConnection(true);
        await vi.advanceTimersByTimeAsync(0);
        notifyDirectChatTyping(1000);
        notifyDirectChatTyping(3999);
        expect(socketMock.typingSent).toEqual([{ roomType: 'direct', roomId: 12 }]);
        notifyDirectChatTyping(4000);
        expect(socketMock.typingSent).toHaveLength(2);
        socketMock.sendSucceeds = false;
        notifyDirectChatTyping(8000);
        socketMock.sendSucceeds = true;
        notifyDirectChatTyping(8001);
        expect(socketMock.typingSent).toHaveLength(3);

        vi.mocked(fetchDirectChat).mockResolvedValueOnce(detail([], { id: 13, canSend: false }));
        closeChat = openDirectChat(13);
        await vi.advanceTimersByTimeAsync(0);
        notifyDirectChatTyping(20000);
        expect(socketMock.typingSent).toHaveLength(3);
    });

    it('연결이 세션 만료를 알리면 폴링을 멈추고 로그인 안내 상태가 된다', async () => {
        stop = startDirectChatSync();
        await openChat();
        emitSessionExpired();
        expect(current().sessionExpired).toBe(true);
        const calls = vi.mocked(fetchDirectChat).mock.calls.length;
        await vi.advanceTimersByTimeAsync(300000);
        expect(fetchDirectChat).toHaveBeenCalledTimes(calls);
    });

    it('프로젝트 신호는 1:1 상태를 바꾸지 않는다', async () => {
        stop = startDirectChatSync();
        await openChat();
        const calls = vi.mocked(fetchDirectChat).mock.calls.length;
        emitSignal({ type: 'message.created', roomType: 'project', roomId: 12, messageId: 1 });
        emitSignal({ type: 'typing', roomType: 'project', roomId: 12, nickname: '샘플' });
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchDirectChat).toHaveBeenCalledTimes(calls);
        expect(current().open?.peerTyping).toBe(false);
    });

    it('정리하면 구독을 해제한다', () => {
        const cleanup = startDirectChatSync();
        expect(subscriberCount()).toBe(3);
        cleanup();
        expect(subscriberCount()).toBe(0);
    });
});

describe('대화 시작', () => {
    it('비로그인은 API를 부르지 않고 프로필로 돌아오는 로그인으로 보낸다', async () => {
        const navigate = vi.fn();
        expect(await startChatWithMember('sample_dev', false, navigate)).toEqual({ ok: true });
        expect(navigate).toHaveBeenCalledWith('/login?next=%2Fusers%2Fsample_dev');
        expect(startDirectChat).not.toHaveBeenCalled();
    });
    it('성공하면 대화방으로 이동한다', async () => {
        vi.mocked(startDirectChat).mockResolvedValue({ id: 12 });
        const navigate = vi.fn();
        expect(await startChatWithMember('sample_dev', true, navigate)).toEqual({ ok: true });
        expect(navigate).toHaveBeenCalledWith('/messages/chats/12');
    });
    it('세션이 만료됐으면 로그인으로 보낸다', async () => {
        vi.mocked(startDirectChat).mockRejectedValue(failure(401));
        const navigate = vi.fn();
        await startChatWithMember('sample_dev', true, navigate);
        expect(navigate).toHaveBeenCalledWith('/login?next=%2Fusers%2Fsample_dev');
    });
    it('그 밖의 실패는 이동하지 않고 안내 문구를 돌려준다', async () => {
        vi.mocked(startDirectChat).mockRejectedValueOnce(failure(400, '본인과는 대화를 시작할 수 없어요.'));
        const navigate = vi.fn();
        expect(await startChatWithMember('sample_dev', true, navigate)).toEqual({ ok: false, error: '본인과는 대화를 시작할 수 없어요.' });
        vi.mocked(startDirectChat).mockRejectedValueOnce(new Error('http://internal'));
        expect(await startChatWithMember('sample_dev', true, navigate)).toEqual({ ok: false, error: '대화를 시작하지 못했어요.' });
        expect(navigate).not.toHaveBeenCalled();
    });
});

describe('사용자 신고', () => {
    it('성공, 중복, 연결 실패를 구분한다', async () => {
        expect(await submitUserReport('sample_dev', '사칭')).toEqual({ ok: true });
        expect(reportUser).toHaveBeenCalledWith('sample_dev', '사칭');
        vi.mocked(reportUser).mockRejectedValueOnce(failure(409, '이미 신고한 사용자예요.'));
        expect(await submitUserReport('sample_dev', '사칭')).toEqual({ ok: false, error: '이미 신고한 사용자예요.' });
        vi.mocked(reportUser).mockRejectedValueOnce(new Error('http://internal'));
        expect(await submitUserReport('sample_dev', '사칭')).toEqual({ ok: false, error: '신고하지 못했어요.' });
    });
    it('401은 로그인 안내 상태가 된다', async () => {
        vi.mocked(reportUser).mockRejectedValueOnce(failure(401, '로그인이 만료됐어요.'));
        await submitUserReport('sample_dev', '사칭');
        expect(current().sessionExpired).toBe(true);
    });
});
