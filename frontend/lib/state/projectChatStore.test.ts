import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
vi.mock('@/lib/messaging/projectChatApi', async importOriginal => ({
    ...await importOriginal<typeof import('@/lib/messaging/projectChatApi')>(),
    fetchProjectChat: vi.fn(),
    fetchProjectChats: vi.fn(),
    fetchProjectUnreadCount: vi.fn(),
    markProjectChatRead: vi.fn(),
    sendProjectMessage: vi.fn(),
}));
vi.mock('@/lib/state/messengerSocketStore', async () => (await import('@/tests/helpers/messengerSocketMock')).messengerSocketStoreMock());
import { DirectChatRequestError } from '@/lib/messaging/directChatApi';
import { fetchProjectChat, fetchProjectChats, fetchProjectUnreadCount, markProjectChatRead, sendProjectMessage } from '@/lib/messaging/projectChatApi';
import { resetLocalStores } from '@/lib/localStore';
import {
    loadOlderProjectMessages, notifyProjectChatTyping, openProjectChat, sendOpenProjectMessage, setProjectChatAtBottom,
    setProjectChatQuery, startProjectChatSync, useProjectChats, watchProjectChatList,
} from './projectChatStore';
import { emitConnection, emitSessionExpired, emitSignal, resetSocketMock, socketMock, subscriberCount } from '@/tests/helpers/messengerSocketMock';
import { projectChatDetailFixture, projectChatListFixture, projectMemberFixture, projectMessageFixture } from '@/tests/helpers/projectChatFixture';
import type { ProjectChatDetail } from '@/types/messaging';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';

function current() {
    let value!: ReturnType<typeof useProjectChats>;
    function Reader() { value = useProjectChats(); return null; }
    renderToStaticMarkup(createElement(Reader));
    return value;
}
const detail = (extra: Partial<ProjectChatDetail> = {}) => projectChatDetailFixture(extra);
const failure = (status: number) => new DirectChatRequestError(status, '실패');

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
    vi.mocked(fetchProjectChat).mockReset().mockResolvedValue(detail());
    vi.mocked(fetchProjectChats).mockReset().mockResolvedValue(projectChatListFixture);
    vi.mocked(fetchProjectUnreadCount).mockReset().mockResolvedValue({ count: 0 });
    vi.mocked(markProjectChatRead).mockReset().mockResolvedValue(undefined);
    vi.mocked(sendProjectMessage).mockReset();
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

async function openChat(projectId = 5) {
    closeChat = openProjectChat(projectId);
    await vi.advanceTimersByTimeAsync(0);
}

describe('배지와 목록', () => {
    it('안 읽은 수를 30초마다 읽고, 프로젝트 탭이 보이는 동안만 목록을 읽는다', async () => {
        vi.mocked(fetchProjectUnreadCount).mockResolvedValue({ count: 4 });
        stop = startProjectChatSync();
        await vi.advanceTimersByTimeAsync(0);
        expect(current().unread).toBe(4);
        expect(fetchProjectChats).not.toHaveBeenCalled();
        const unwatch = watchProjectChatList();
        await vi.advanceTimersByTimeAsync(0);
        expect(current().list).toMatchObject({ status: 'ready', items: projectChatListFixture.items });
        unwatch();
        const calls = vi.mocked(fetchProjectChats).mock.calls.length;
        await vi.advanceTimersByTimeAsync(30000);
        expect(fetchProjectUnreadCount).toHaveBeenCalledTimes(3);
        expect(fetchProjectChats).toHaveBeenCalledTimes(calls);
    });

    it('검색어로 다시 조회한다', async () => {
        stop = startProjectChatSync();
        const unwatch = watchProjectChatList();
        await vi.advanceTimersByTimeAsync(0);
        setProjectChatQuery(' 샘플 ');
        await vi.advanceTimersByTimeAsync(0);
        expect(vi.mocked(fetchProjectChats).mock.lastCall?.[0]).toEqual({ q: '샘플' });
        unwatch();
    });

    it('401이면 폴링을 멈추고 로그인 안내 상태가 된다', async () => {
        vi.mocked(fetchProjectUnreadCount).mockRejectedValue(failure(401));
        stop = startProjectChatSync();
        await vi.advanceTimersByTimeAsync(0);
        expect(current().sessionExpired).toBe(true);
        const calls = vi.mocked(fetchProjectUnreadCount).mock.calls.length;
        await vi.advanceTimersByTimeAsync(300000);
        expect(fetchProjectUnreadCount).toHaveBeenCalledTimes(calls);
    });
});

describe('열린 채팅', () => {
    it('처음에는 최신 메시지를 읽고 이후 after로 새 메시지만 읽어 멤버와 함께 갱신한다', async () => {
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ messages: [projectMessageFixture(10, 2)], nextBefore: 9 }));
        await openChat();
        expect(fetchProjectChat).toHaveBeenLastCalledWith(5, { size: 50 }, expect.any(AbortSignal));
        expect(current().open).toMatchObject({ status: 'ready', title: '샘플 프로젝트', chatStatus: 'IN_PROGRESS', nextBefore: 9 });
        const members = [projectMemberFixture(1, { me: true }), projectMemberFixture(2, { lastReadMessageId: 11 })];
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ members, messages: [projectMessageFixture(11, 2)] }));
        await vi.advanceTimersByTimeAsync(5000);
        expect(fetchProjectChat).toHaveBeenLastCalledWith(5, { after: 10, size: 50 }, expect.any(AbortSignal));
        expect(current().open?.messages.map(message => message.id)).toEqual([10, 11]);
        expect(current().open?.members).toEqual(members);
        expect(current().open?.nextBefore).toBe(9);
    });

    it('입장할 수 없으면(404) missing으로 바꾸고 조회를 멈춘다', async () => {
        vi.mocked(fetchProjectChat).mockRejectedValue(failure(404));
        await openChat();
        expect(current().open?.status).toBe('missing');
        await vi.advanceTimersByTimeAsync(60000);
        expect(fetchProjectChat).toHaveBeenCalledTimes(1);
    });

    it('이전 메시지를 앞에 붙인다', async () => {
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ messages: [projectMessageFixture(10, 2)], nextBefore: 10 }));
        await openChat();
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ messages: [projectMessageFixture(9, 3)], nextBefore: null }));
        await loadOlderProjectMessages();
        expect(fetchProjectChat).toHaveBeenLastCalledWith(5, { before: 10 });
        expect(current().open?.messages.map(message => message.id)).toEqual([9, 10]);
    });

    it('맨 아래에서 다른 팀원의 메시지를 보면 한 번만 읽음 처리하고, 숨긴 탭이나 위로 올린 동안은 미룬다', async () => {
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ messages: [projectMessageFixture(10, 2), projectMessageFixture(11, 1)] }));
        await openChat();
        expect(markProjectChatRead).toHaveBeenCalledWith(5, 11);
        vi.mocked(markProjectChatRead).mockClear();
        setProjectChatAtBottom(false);
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ messages: [projectMessageFixture(12, 3)] }));
        await vi.advanceTimersByTimeAsync(5000);
        expect(markProjectChatRead).not.toHaveBeenCalled();
        hidden = true;
        setProjectChatAtBottom(true);
        expect(markProjectChatRead).not.toHaveBeenCalled();
        hidden = false;
        setProjectChatAtBottom(true);
        expect(markProjectChatRead).toHaveBeenCalledWith(5, 12);
    });

    it('보낸 메시지를 합치고 종료된 프로젝트(409)는 안내와 함께 다시 읽는다', async () => {
        await openChat();
        vi.mocked(sendProjectMessage).mockResolvedValueOnce(projectMessageFixture(20, 1));
        expect(await sendOpenProjectMessage('안녕')).toEqual({ ok: true });
        expect(current().open?.messages.map(message => message.id)).toEqual([20]);
        vi.mocked(sendProjectMessage).mockRejectedValueOnce(new DirectChatRequestError(409, '종료된 프로젝트 채팅에는 메시지를 보낼 수 없어요.'));
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ canSend: false, status: 'COMPLETED' }));
        expect(await sendOpenProjectMessage('안녕')).toEqual({ ok: false, error: '종료된 프로젝트 채팅에는 메시지를 보낼 수 없어요.' });
        await vi.advanceTimersByTimeAsync(0);
        expect(current().open?.canSend).toBe(false);
    });
});

describe('실시간 신호', () => {
    const created = (roomId: number, messageId: number) => ({ type: 'message.created' as const, roomType: 'project' as const, roomId, messageId });
    const read = (memberTag: string | null, lastReadMessageId: number) => ({ type: 'message.read' as const, roomType: 'project' as const, roomId: 5, lastReadMessageId, memberTag });
    const typing = (nickname: string | null, roomId = 5) => ({ type: 'typing' as const, roomType: 'project' as const, roomId, nickname });

    it('연결되면 60초마다만 조회하고, 열린 방의 새 메시지 신호는 바로 after로 읽는다', async () => {
        socketMock.connected = true;
        stop = startProjectChatSync();
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ messages: [projectMessageFixture(10, 2)] }));
        await openChat();
        const calls = vi.mocked(fetchProjectChat).mock.calls.length;
        await vi.advanceTimersByTimeAsync(10000);
        expect(fetchProjectChat).toHaveBeenCalledTimes(calls);
        emitSignal(created(5, 11));
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchProjectChat).toHaveBeenLastCalledWith(5, { after: 10, size: 50 }, expect.any(AbortSignal));
        const unread = vi.mocked(fetchProjectUnreadCount).mock.calls.length;
        emitSignal(created(6, 1));
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchProjectUnreadCount).toHaveBeenCalledTimes(unread + 1);
        emitConnection(false);
        await vi.advanceTimersByTimeAsync(5000);
        expect(vi.mocked(fetchProjectChat).mock.calls.length).toBeGreaterThan(calls + 1);
    });

    it('숨긴 탭도 새 메시지 신호로 열린 방과 안 읽은 수를 바로 읽는다', async () => {
        socketMock.connected = true;
        stop = startProjectChatSync();
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ messages: [projectMessageFixture(10, 2)] }));
        await openChat();
        hidden = true;
        const calls = vi.mocked(fetchProjectChat).mock.calls.length;
        emitSignal(created(5, 11));
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchProjectChat).toHaveBeenCalledTimes(calls + 1);
        expect(fetchProjectChat).toHaveBeenLastCalledWith(5, { after: 10, size: 50 }, expect.any(AbortSignal));
        const unread = vi.mocked(fetchProjectUnreadCount).mock.calls.length;
        emitSignal(created(6, 1));
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchProjectUnreadCount).toHaveBeenCalledTimes(unread + 1);
    });

    it('읽음 신호는 태그로 팀원을 찾아 앞으로만 옮기고, 찾지 못하면 다시 읽는다', async () => {
        stop = startProjectChatSync();
        const members = [projectMemberFixture(1, { me: true }), projectMemberFixture(2, { lastReadMessageId: 12 })];
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ members }));
        await openChat();
        const chatCalls = vi.mocked(fetchProjectChat).mock.calls.length;
        const unreadCalls = vi.mocked(fetchProjectUnreadCount).mock.calls.length;
        emitSignal(read('member_2', 10));
        expect(current().open?.members[1].lastReadMessageId).toBe(12);
        emitSignal(read('member_2', 15));
        expect(current().open?.members[1].lastReadMessageId).toBe(15);
        await vi.advanceTimersByTimeAsync(0);
        // 다른 팀원의 읽음은 내 안 읽은 수를 바꾸지 않는다.
        expect(fetchProjectChat).toHaveBeenCalledTimes(chatCalls);
        expect(fetchProjectUnreadCount).toHaveBeenCalledTimes(unreadCalls);
        emitSignal(read('member_1', 15));
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchProjectUnreadCount).toHaveBeenCalledTimes(unreadCalls + 1);
        emitSignal(read(null, 16));
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchProjectChat).toHaveBeenCalledTimes(chatCalls + 1);
    });

    it('입력 중인 팀원을 여럿 표시하고, 5초가 지나거나 그 팀원의 메시지가 오면 그 팀원만 지운다', async () => {
        stop = startProjectChatSync();
        await openChat();
        emitSignal(typing('주최자'));
        emitSignal(typing(null));
        emitSignal(typing('다른방', 6));
        expect(current().open?.typing).toEqual(['주최자', '팀원']);
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ messages: [projectMessageFixture(10, 2, { sender: { memberId: 2, tag: 'member_2', nickname: '주최자' } })] }));
        emitSignal(created(5, 10));
        await vi.advanceTimersByTimeAsync(0);
        expect(current().open?.typing).toEqual(['팀원']);
        await vi.advanceTimersByTimeAsync(5000);
        expect(current().open?.typing).toEqual([]);
    });

    it('입력 중 신호는 연결돼 있고 보낼 수 있는 방에서만 3초에 한 번 보낸다', async () => {
        stop = startProjectChatSync();
        await openChat();
        notifyProjectChatTyping(1000);
        expect(socketMock.typingSent).toEqual([]);
        emitConnection(true);
        await vi.advanceTimersByTimeAsync(0);
        notifyProjectChatTyping(1000);
        notifyProjectChatTyping(3999);
        notifyProjectChatTyping(4000);
        expect(socketMock.typingSent).toEqual([{ roomType: 'project', roomId: 5 }, { roomType: 'project', roomId: 5 }]);
        vi.mocked(fetchProjectChat).mockResolvedValueOnce(detail({ projectId: 6, canSend: false }));
        closeChat = openProjectChat(6);
        await vi.advanceTimersByTimeAsync(0);
        notifyProjectChatTyping(20000);
        expect(socketMock.typingSent).toHaveLength(2);
    });

    it('1:1 신호는 무시하고, 연결이 세션 만료를 알리면 멈추며, 정리하면 구독을 해제한다', async () => {
        stop = startProjectChatSync();
        await openChat();
        const calls = vi.mocked(fetchProjectChat).mock.calls.length;
        emitSignal({ type: 'message.created', roomType: 'direct', roomId: 5, messageId: 1 });
        await vi.advanceTimersByTimeAsync(0);
        expect(fetchProjectChat).toHaveBeenCalledTimes(calls);
        emitSessionExpired();
        expect(current().sessionExpired).toBe(true);
        stop();
        stop = undefined;
        expect(subscriberCount()).toBe(0);
    });
});
