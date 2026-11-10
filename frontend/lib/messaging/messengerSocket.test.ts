import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
    checkMessengerSession, createMessengerSocket, messengerSocketUrl, parseMessengerFrame,
    type MessengerSocketConnection, type MessengerSocketOptions, type SessionState,
} from './messengerSocket';

class FakeConnection implements MessengerSocketConnection {
    readyState = 0;
    onopen: (() => void) | null = null;
    onmessage: ((event: { data: unknown }) => void) | null = null;
    onclose: ((event: { code: number }) => void) | null = null;
    onerror: (() => void) | null = null;
    sent: string[] = [];
    closedWith: number | undefined;
    constructor(readonly url: string) {}
    send(data: string) { this.sent.push(data); }
    close(code?: number) { this.closedWith = code; this.readyState = 3; }
    open() { this.readyState = 1; this.onopen?.(); }
    receive(data: unknown) { this.onmessage?.({ data }); }
    drop(code: number) { this.readyState = 3; this.onclose?.({ code }); }
}

let connections: FakeConnection[];
let session: SessionState;
let options: MessengerSocketOptions & { onSignal: ReturnType<typeof vi.fn>; onConnectionChange: ReturnType<typeof vi.fn>; onSessionExpired: ReturnType<typeof vi.fn>; checkSession: ReturnType<typeof vi.fn> };

const last = () => connections.at(-1)!;

beforeEach(() => {
    vi.useFakeTimers();
    connections = [];
    session = 'active';
    options = {
        url: () => 'ws://localhost:8080/ws/messages',
        connect: url => { const connection = new FakeConnection(url); connections.push(connection); return connection; },
        checkSession: vi.fn(async () => session),
        onSignal: vi.fn(),
        onConnectionChange: vi.fn(),
        onSessionExpired: vi.fn(),
        // 무작위 지연을 0으로 두면 최소 지연(상한의 절반)만 기다린다.
        random: () => 0,
    };
});
afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
});

describe('연결 주소', () => {
    it('HTTPS는 같은 주소의 wss, HTTP는 포트가 있으면 그 포트, 없으면 같은 주소다', () => {
        expect(messengerSocketUrl({ protocol: 'https:', host: 'devmozip.example.com', hostname: 'devmozip.example.com' }, '8080'))
            .toBe('wss://devmozip.example.com/ws/messages');
        expect(messengerSocketUrl({ protocol: 'http:', host: 'localhost:3000', hostname: 'localhost' }, '8080')).toBe('ws://localhost:8080/ws/messages');
        expect(messengerSocketUrl({ protocol: 'http:', host: 'localhost:3000', hostname: 'localhost' }, undefined)).toBe('ws://localhost:3000/ws/messages');
        for (const port of ['', ' ', '0', '65536', 'abc', '80/evil', '8080:']) {
            expect(messengerSocketUrl({ protocol: 'http:', host: 'localhost:3000', hostname: 'localhost' }, port)).toBe('ws://localhost:3000/ws/messages');
        }
        expect(messengerSocketUrl({ protocol: 'file:', host: '', hostname: '' }, '8080')).toBeNull();
    });
});

describe('프레임', () => {
    it('계약의 신호와 ping만 받아들인다', () => {
        expect(parseMessengerFrame('{"type":"ping"}')).toBe('ping');
        expect(parseMessengerFrame('{"type":"message.created","roomType":"direct","roomId":12,"messageId":341}'))
            .toEqual({ type: 'message.created', roomType: 'direct', roomId: 12, messageId: 341 });
        expect(parseMessengerFrame('{"type":"message.read","roomType":"project","roomId":5,"lastReadMessageId":9,"memberTag":"dev"}'))
            .toEqual({ type: 'message.read', roomType: 'project', roomId: 5, lastReadMessageId: 9, memberTag: 'dev' });
        expect(parseMessengerFrame('{"type":"message.read","roomType":"direct","roomId":5,"lastReadMessageId":9}'))
            .toMatchObject({ memberTag: null });
        expect(parseMessengerFrame('{"type":"typing","roomType":"project","roomId":5,"nickname":"샘플"}'))
            .toEqual({ type: 'typing', roomType: 'project', roomId: 5, nickname: '샘플' });
        expect(parseMessengerFrame('{"type":"typing","roomType":"direct","roomId":12}')).toMatchObject({ nickname: null });
        for (const frame of ['', 'x', '[]', 'null', '{}', '{"type":"message.created","roomType":"group","roomId":1,"messageId":1}',
            '{"type":"message.created","roomType":"direct","roomId":0,"messageId":1}', '{"type":"message.created","roomType":"direct","roomId":1}',
            '{"type":"message.read","roomType":"direct","roomId":1,"lastReadMessageId":"1"}', '{"type":"unknown","roomType":"direct","roomId":1}']) {
            expect(parseMessengerFrame(frame)).toBeNull();
        }
        expect(parseMessengerFrame(new ArrayBuffer(2))).toBeNull();
    });
});

describe('연결', () => {
    it('열리면 연결 상태를 알리고, ping에 pong으로 답하며 신호를 전달한다', () => {
        const socket = createMessengerSocket(options);
        socket.start();
        expect(last().url).toBe('ws://localhost:8080/ws/messages');
        expect(socket.isConnected()).toBe(false);
        last().open();
        expect(options.onConnectionChange).toHaveBeenLastCalledWith(true);
        last().receive('{"type":"ping"}');
        expect(last().sent).toEqual(['{"type":"pong"}']);
        last().receive('{"type":"message.created","roomType":"direct","roomId":12,"messageId":341}');
        last().receive('잘못된 프레임');
        expect(options.onSignal).toHaveBeenCalledTimes(1);
        socket.stop();
    });

    it('끊기면 지수 백오프로 다시 연결하고, 열리면 간격을 되돌린다', async () => {
        const socket = createMessengerSocket(options);
        socket.start();
        last().open();
        last().drop(1001);
        expect(options.onConnectionChange).toHaveBeenLastCalledWith(false);
        await vi.advanceTimersByTimeAsync(499);
        expect(connections).toHaveLength(1);
        await vi.advanceTimersByTimeAsync(1);
        expect(connections).toHaveLength(2);
        // 열리기 전에 끊기면 세션을 확인하고 두 배 간격으로 다시 시도한다.
        last().drop(1006);
        await vi.advanceTimersByTimeAsync(999);
        expect(options.checkSession).toHaveBeenCalledTimes(1);
        expect(connections).toHaveLength(2);
        await vi.advanceTimersByTimeAsync(1);
        expect(connections).toHaveLength(3);
        last().open();
        last().drop(4500);
        await vi.advanceTimersByTimeAsync(500);
        expect(connections).toHaveLength(4);
        socket.stop();
    });

    it('재연결 간격은 30초를 넘지 않고 무작위 지연을 더한다', async () => {
        const socket = createMessengerSocket({ ...options, random: () => 0.999 });
        socket.start();
        for (let i = 0; i < 8; i++) {
            last().drop(1006);
            await vi.advanceTimersByTimeAsync(30000);
        }
        expect(connections).toHaveLength(9);
        last().drop(1006);
        await vi.advanceTimersByTimeAsync(29000);
        expect(connections).toHaveLength(9);
        await vi.advanceTimersByTimeAsync(1000);
        expect(connections).toHaveLength(10);
        socket.stop();
    });

    it('토큰 만료(4401)는 세션을 갱신한 뒤 바로 다시 연결한다', async () => {
        const socket = createMessengerSocket(options);
        socket.start();
        last().open();
        last().drop(4401);
        await vi.advanceTimersByTimeAsync(0);
        expect(options.checkSession).toHaveBeenCalledTimes(1);
        expect(connections).toHaveLength(2);
        socket.stop();
    });

    it('세션이 끝났으면 다시 연결하지 않고 알린다', async () => {
        session = 'expired';
        const socket = createMessengerSocket(options);
        socket.start();
        last().open();
        last().drop(4401);
        await vi.advanceTimersByTimeAsync(60000);
        expect(options.onSessionExpired).toHaveBeenCalledTimes(1);
        expect(connections).toHaveLength(1);

        socket.start();
        last().drop(1006);
        await vi.advanceTimersByTimeAsync(60000);
        expect(options.onSessionExpired).toHaveBeenCalledTimes(2);
        expect(connections).toHaveLength(2);
    });

    it('세션을 확인하지 못하면 간격을 늘려 다시 시도한다', async () => {
        session = 'unknown';
        const socket = createMessengerSocket(options);
        socket.start();
        last().open();
        last().drop(4401);
        await vi.advanceTimersByTimeAsync(499);
        expect(connections).toHaveLength(1);
        await vi.advanceTimersByTimeAsync(1);
        expect(connections).toHaveLength(2);
        expect(options.onSessionExpired).not.toHaveBeenCalled();
        socket.stop();
    });

    it('정지 회원(4403)은 다시 연결하지 않는다', async () => {
        const socket = createMessengerSocket(options);
        socket.start();
        last().open();
        last().drop(4403);
        await vi.advanceTimersByTimeAsync(120000);
        expect(connections).toHaveLength(1);
        expect(options.checkSession).not.toHaveBeenCalled();
    });

    it('멈추면 1000으로 닫고 다시 연결하지 않으며, 이전 연결의 늦은 이벤트를 무시한다', async () => {
        const socket = createMessengerSocket(options);
        socket.start();
        const first = last();
        first.open();
        socket.stop();
        expect(first.closedWith).toBe(1000);
        expect(options.onConnectionChange).toHaveBeenLastCalledWith(false);
        first.receive('{"type":"message.created","roomType":"direct","roomId":12,"messageId":1}');
        first.drop(1000);
        await vi.advanceTimersByTimeAsync(60000);
        expect(connections).toHaveLength(1);
        expect(options.onSignal).not.toHaveBeenCalled();
    });

    it('세션 확인 중에 멈추면 다시 연결하지 않고, 확인 결과가 만료여도 알리지 않는다', async () => {
        let resolve!: (state: SessionState) => void;
        options.checkSession.mockImplementation(() => new Promise(done => { resolve = done; }));
        const socket = createMessengerSocket(options);
        socket.start();
        last().drop(1006);
        socket.stop();
        resolve('active');
        await vi.advanceTimersByTimeAsync(60000);
        expect(connections).toHaveLength(1);

        // 로그아웃하거나 토글을 켠 뒤라면 로그인 만료 안내를 띄우지 않는다.
        socket.start();
        last().open();
        last().drop(4401);
        socket.stop();
        resolve('expired');
        await vi.advanceTimersByTimeAsync(60000);
        expect(options.onSessionExpired).not.toHaveBeenCalled();
        expect(connections).toHaveLength(2);
    });

    it('연결돼 있을 때만 보낸다', () => {
        const socket = createMessengerSocket(options);
        expect(socket.send({ type: 'typing' })).toBe(false);
        socket.start();
        expect(socket.send({ type: 'typing' })).toBe(false);
        last().open();
        expect(socket.send({ type: 'typing', roomType: 'direct', roomId: 12 })).toBe(true);
        expect(last().sent).toEqual(['{"type":"typing","roomType":"direct","roomId":12}']);
        socket.stop();
        expect(socket.send({ type: 'typing' })).toBe(false);
    });

    it('주소가 없으면 연결하지 않는다', () => {
        const socket = createMessengerSocket({ ...options, url: () => null });
        socket.start();
        expect(connections).toHaveLength(0);
        socket.stop();
    });
});

describe('세션 확인', () => {
    it('identity가 있으면 active, 없으면 expired, 응답 실패나 연결 실패는 unknown이다', async () => {
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);
        fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ identity: { username: 'a', role: 'USER' } })));
        expect(await checkMessengerSession()).toBe('active');
        fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ identity: null })));
        expect(await checkMessengerSession()).toBe('expired');
        fetchMock.mockResolvedValueOnce(new Response('{}', { status: 502 }));
        expect(await checkMessengerSession()).toBe('unknown');
        fetchMock.mockRejectedValueOnce(new Error('offline'));
        expect(await checkMessengerSession()).toBe('unknown');
        expect(fetchMock).toHaveBeenCalledWith('/api/auth/session', { cache: 'no-store' });
    });
});

it('프로젝트 강퇴 신호는 프로젝트 방과 유효한 투표 정보에만 허용한다',()=>{
 const frame={type:'project.members.changed',roomType:'project',roomId:7,voteId:8,status:'PASSED'};
 expect(parseMessengerFrame(JSON.stringify(frame))).toEqual(frame);
 for(const invalid of [{...frame,roomType:'direct'},{...frame,voteId:0},{...frame,status:'UNKNOWN'}])expect(parseMessengerFrame(JSON.stringify(invalid))).toBeNull();
});
