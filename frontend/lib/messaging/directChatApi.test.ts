import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    DirectChatRequestError, directMessageBody, fetchDirectChat, fetchDirectChats, fetchDirectUnreadCount, markDirectChatRead,
    parseDirectChatDetail, parseDirectChatList, parseDirectChatMessage, parseStartedChat, parseUnreadCount, reportUser,
    sendDirectMessage, startDirectChat, validChatId,
} from './directChatApi';
import { directChatDetailFixture, directChatListFixture, directChatMessageFixture, directChatPeerFixture } from '@/tests/helpers/directChatFixture';

const fetchMock = vi.fn();
const lastCall = () => fetchMock.mock.calls[fetchMock.mock.calls.length - 1] as [string, RequestInit];
beforeEach(() => { fetchMock.mockReset(); vi.stubGlobal('fetch', fetchMock); });
afterEach(() => vi.unstubAllGlobals());

describe('메시지 응답 검증', () => {
    it('정상 응답을 그대로 읽는다', () => {
        expect(parseDirectChatList(directChatListFixture)).toEqual(directChatListFixture);
        expect(parseDirectChatDetail(directChatDetailFixture)).toEqual(directChatDetailFixture);
        expect(parseStartedChat({ id: 12 })).toEqual({ id: 12 });
        expect(parseUnreadCount({ count: 0 })).toEqual({ count: 0 });
    });
    it('알 수 없는 필드는 브라우저로 전달하지 않는다', () => {
        const list = parseDirectChatList({ ...directChatListFixture, token: 'secret', items: [{ ...directChatListFixture.items[0], secret: true,
            peer: { ...directChatPeerFixture, email: 'private@devmozip.test', passwordHash: 'hash' }, lastMessage: { ...directChatMessageFixture, chatMemberId: 9 } }] });
        expect(list).toEqual(directChatListFixture);
    });
    it('태그와 닉네임이 없는 상대를 허용한다', () => {
        const peer = { ...directChatPeerFixture, tag: null, nickname: null, collaborationStatus: null };
        expect(parseDirectChatDetail({ ...directChatDetailFixture, peer }).peer).toEqual(peer);
    });
    it('시각은 오프셋을 유지하고 밀리초까지만 남긴다', () => {
        expect(parseDirectChatMessage({ ...directChatMessageFixture, createdAt: '2026-11-06T10:15:00.123456+09:00' }).createdAt).toBe('2026-11-06T10:15:00.123+09:00');
        expect(parseDirectChatMessage({ ...directChatMessageFixture, createdAt: '2026-11-06T01:15:00Z' }).createdAt).toBe('2026-11-06T01:15:00Z');
    });
    it.each([
        ['배열이 아닌 목록', { items: 'x', nextCursor: null }],
        ['잘못된 커서', { items: [], nextCursor: 'abc' }],
        ['방 ID 누락', { items: [{ ...directChatListFixture.items[0], id: undefined }], nextCursor: null }],
        ['음수 안 읽은 수', { items: [{ ...directChatListFixture.items[0], unreadCount: -1 }], nextCursor: null }],
        ['경로를 벗어나는 상대 태그', { items: [{ ...directChatListFixture.items[0], peer: { ...directChatPeerFixture, tag: '../admin' } }], nextCursor: null }],
        ['상대 정보 누락', { items: [{ ...directChatListFixture.items[0], peer: null }], nextCursor: null }],
    ])('목록의 %s를 거부한다', (_name, value) => {
        expect(() => parseDirectChatList(value)).toThrow();
    });
    it.each([
        ['오프셋 없는 시각', { ...directChatMessageFixture, createdAt: '2026-11-06T10:15:00' }],
        ['해석할 수 없는 시각', { ...directChatMessageFixture, createdAt: 'x+09:00' }],
        ['문자열이 아닌 본문', { ...directChatMessageFixture, body: 1 }],
        ['boolean이 아닌 mine', { ...directChatMessageFixture, mine: 'true' }],
        ['소수 ID', { ...directChatMessageFixture, id: 1.5 }],
    ])('메시지의 %s를 거부한다', (_name, value) => {
        expect(() => parseDirectChatMessage(value)).toThrow();
    });
    it.each([null, [], 'text', { id: 0 }, { id: '12' }])('시작 응답 %j를 거부한다', value => {
        expect(() => parseStartedChat(value)).toThrow();
    });
    it('상세 응답의 잘못된 값을 거부한다', () => {
        expect(() => parseDirectChatDetail({ ...directChatDetailFixture, canSend: 'yes' })).toThrow();
        expect(() => parseDirectChatDetail({ ...directChatDetailFixture, peerLastReadMessageId: -1 })).toThrow();
        expect(() => parseDirectChatDetail({ ...directChatDetailFixture, nextBefore: '1' })).toThrow();
    });
});

describe('입력 검증', () => {
    it('본문은 앞뒤 공백을 뺀 뒤 1~2,000자만 허용한다', () => {
        expect(directMessageBody('  안녕  ')).toBe('안녕');
        expect(directMessageBody('a')).toBe('a');
        expect(directMessageBody('a'.repeat(2000))).toHaveLength(2000);
        expect(directMessageBody(` ${'a'.repeat(2000)} `)).toHaveLength(2000);
        for (const invalid of ['', '  \n ', 'a'.repeat(2001), 123, null, undefined]) expect(directMessageBody(invalid)).toBeNull();
    });
    it('경로에 넣는 대화방 ID는 양의 정수만 허용한다', () => {
        expect(validChatId('12')).toBe(true);
        for (const invalid of ['0', '012', '-1', '1.5', 'abc', '1/../2', '', '99999999999999999']) expect(validChatId(invalid)).toBe(false);
    });
});

describe('브라우저 호출', () => {
    it('목록은 값이 있는 조건만 BFF로 보낸다', async () => {
        fetchMock.mockImplementation(async () => Response.json(directChatListFixture));
        expect(await fetchDirectChats({ q: '샘플 %', cursor: 5, size: undefined })).toEqual(directChatListFixture);
        expect(lastCall()[0]).toBe('/api/messages/chats?q=%EC%83%98%ED%94%8C+%25&cursor=5');
        expect(lastCall()[1].cache).toBe('no-store');
        await fetchDirectChats();
        expect(lastCall()[0]).toBe('/api/messages/chats');
    });
    it('상세, 안 읽은 수를 BFF로 조회한다', async () => {
        fetchMock.mockResolvedValueOnce(Response.json(directChatDetailFixture));
        expect(await fetchDirectChat(12, { after: 339 })).toEqual(directChatDetailFixture);
        expect(lastCall()[0]).toBe('/api/messages/chats/12?after=339');
        fetchMock.mockResolvedValueOnce(Response.json({ count: 3 }));
        expect(await fetchDirectUnreadCount()).toEqual({ count: 3 });
        expect(lastCall()[0]).toBe('/api/messages/chats/unread-count');
    });
    it('대화 시작은 태그만 보내고 방 ID를 돌려준다', async () => {
        fetchMock.mockResolvedValue(Response.json({ id: 12 }));
        expect(await startDirectChat('sample_dev')).toEqual({ id: 12 });
        expect(lastCall()[0]).toBe('/api/messages/chats');
        expect(lastCall()[1]).toMatchObject({ method: 'POST', headers: { 'Content-Type': 'application/json' } });
        expect(JSON.parse(String(lastCall()[1].body))).toEqual({ targetSlug: 'sample_dev' });
        await expect(startDirectChat('../admin')).rejects.toMatchObject({ status: 400 });
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });
    it('전송은 공백을 뺀 본문을 보내고 길이를 넘으면 요청하지 않는다', async () => {
        fetchMock.mockResolvedValue(Response.json(directChatMessageFixture, { status: 201 }));
        expect(await sendDirectMessage(12, '  안녕하세요 ')).toEqual(directChatMessageFixture);
        expect(lastCall()[0]).toBe('/api/messages/chats/12');
        expect(JSON.parse(String(lastCall()[1].body))).toEqual({ body: '안녕하세요' });
        fetchMock.mockClear();
        await expect(sendDirectMessage(12, 'a'.repeat(2001))).rejects.toMatchObject({ status: 400 });
        await expect(sendDirectMessage(12, '   ')).rejects.toBeInstanceOf(DirectChatRequestError);
        expect(fetchMock).not.toHaveBeenCalled();
    });
    it('읽음과 신고는 본문 없는 성공 응답을 처리한다', async () => {
        fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
        await expect(markDirectChatRead(12, 340)).resolves.toBeUndefined();
        expect(lastCall()[0]).toBe('/api/messages/chats/12/read');
        expect(lastCall()[1].method).toBe('PATCH');
        expect(JSON.parse(String(lastCall()[1].body))).toEqual({ lastReadMessageId: 340 });
        fetchMock.mockResolvedValueOnce(new Response(null, { status: 201 }));
        await expect(reportUser('sample_dev', '사칭')).resolves.toBeUndefined();
        expect(lastCall()[0]).toBe('/api/users/sample_dev/reports');
        expect(JSON.parse(String(lastCall()[1].body))).toEqual({ reason: '사칭' });
    });
    it('BFF 오류를 상태와 안내 문구로 바꾼다', async () => {
        fetchMock.mockResolvedValueOnce(Response.json({ message: '이미 신고한 사용자예요.' }, { status: 409 }));
        await expect(reportUser('sample_dev', '사칭')).rejects.toMatchObject({ status: 409, message: '이미 신고한 사용자예요.' });
        fetchMock.mockResolvedValueOnce(new Response('internal', { status: 500 }));
        await expect(fetchDirectChats()).rejects.toMatchObject({ status: 500, message: '메시지 요청을 완료하지 못했어요.' });
    });
    it('잘못된 성공 응답을 거부한다', async () => {
        fetchMock.mockResolvedValue(Response.json({ items: 'x' }));
        await expect(fetchDirectChats()).rejects.toThrow('잘못된 메시지 응답');
    });
});
