// 메신저 WebSocket 연결(11.8). 신호만 받고 본문은 BFF로 조회한다. 1:1과 프로젝트 채팅이 연결 하나를 함께 쓴다.
export type MessengerRoomType = 'direct' | 'project';

export type MessengerSignal =
    | { type: 'message.created'; roomType: MessengerRoomType; roomId: number; messageId: number }
    | { type: 'message.read'; roomType: MessengerRoomType; roomId: number; lastReadMessageId: number; memberTag: string | null }
    | { type: 'typing'; roomType: MessengerRoomType; roomId: number; nickname: string | null };

export type SessionState = 'active' | 'expired' | 'unknown';

/** 브라우저 WebSocket 중 이 모듈이 쓰는 부분. 테스트에서 가짜 연결로 바꾼다. */
export interface MessengerSocketConnection {
    readonly readyState: number;
    onopen: (() => void) | null;
    onmessage: ((event: { data: unknown }) => void) | null;
    onclose: ((event: { code: number }) => void) | null;
    onerror: (() => void) | null;
    send(data: string): void;
    close(code?: number, reason?: string): void;
}

const OPEN = 1;
const PATH = '/ws/messages';
const RECONNECT_BASE = 1000;
const RECONNECT_MAX = 30000;
export const CLOSE = { normal: 1000, handshakeFailed: 1006, tokenExpired: 4401, suspended: 4403 } as const;

const identifier = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
const roomType = (value: unknown): value is MessengerRoomType => value === 'direct' || value === 'project';
const optionalText = (value: unknown) => (typeof value === 'string' ? value : null);

/** 서버 프레임을 확인한다. ping이면 'ping', 알 수 없거나 형식이 틀리면 null이다. */
export function parseMessengerFrame(data: unknown): MessengerSignal | 'ping' | null {
    if (typeof data !== 'string') return null;
    let frame: unknown;
    try {
        frame = JSON.parse(data);
    } catch {
        return null;
    }
    if (!frame || typeof frame !== 'object' || Array.isArray(frame)) return null;
    const value = frame as Record<string, unknown>;
    if (value.type === 'ping') return 'ping';
    if (!roomType(value.roomType) || !identifier(value.roomId)) return null;
    const room = { roomType: value.roomType, roomId: value.roomId };
    if (value.type === 'message.created' && identifier(value.messageId)) return { type: 'message.created', ...room, messageId: value.messageId };
    if (value.type === 'message.read' && identifier(value.lastReadMessageId)) {
        return { type: 'message.read', ...room, lastReadMessageId: value.lastReadMessageId, memberTag: optionalText(value.memberTag) };
    }
    if (value.type === 'typing') return { type: 'typing', ...room, nickname: optionalText(value.nickname) };
    return null;
}

/**
 * 연결 주소. HTTPS 페이지는 같은 Origin의 wss로 연결한다(운영은 nginx가 백엔드로 넘긴다).
 * HTTP 페이지는 portOverride가 있으면 그 포트의 백엔드로 바로 연결한다. 쿠키는 포트를 구분하지 않는다.
 */
export function messengerSocketUrl(location: { protocol: string; host: string; hostname: string }, portOverride?: string): string | null {
    if (location.protocol === 'https:') return `wss://${location.host}${PATH}`;
    if (location.protocol !== 'http:') return null;
    const port = portOverride?.trim();
    if (port && /^[1-9][0-9]{0,4}$/.test(port) && Number(port) <= 65535) return `ws://${location.hostname}:${port}${PATH}`;
    return `ws://${location.host}${PATH}`;
}

export interface MessengerSocketOptions {
    /** null이면 연결하지 않는다(서버 렌더링 등). */
    url: () => string | null;
    connect?: (url: string) => MessengerSocketConnection;
    /** 토큰을 갱신하면서 로그인 상태를 확인한다. BFF의 proxy가 만료 토큰을 재발급한다. */
    checkSession: () => Promise<SessionState>;
    onSignal: (signal: MessengerSignal) => void;
    onConnectionChange: (connected: boolean) => void;
    onSessionExpired: () => void;
    random?: () => number;
}

/**
 * 끊기면 지수 백오프에 무작위 지연을 더해 다시 연결한다. 배포 때 모든 연결이 한꺼번에 끊기기 때문이다.
 * 핸드셰이크 거부는 브라우저가 상태 코드를 알려 주지 않고 열리기 전의 1006으로만 보이므로, 세션을 확인한 뒤 판단한다.
 */
export function createMessengerSocket(options: MessengerSocketOptions) {
    const connect = options.connect ?? ((url: string) => new WebSocket(url) as unknown as MessengerSocketConnection);
    const random = options.random ?? Math.random;
    let active = false;
    let socket: MessengerSocketConnection | null = null;
    let connected = false;
    let attempts = 0;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const setConnected = (next: boolean) => {
        if (connected === next) return;
        connected = next;
        options.onConnectionChange(next);
    };

    function open() {
        timer = undefined;
        if (!active || socket) return;
        const url = options.url();
        if (!url) return;
        let current: MessengerSocketConnection;
        try {
            current = connect(url);
        } catch {
            reconnectLater();
            return;
        }
        let opened = false;
        socket = current;
        current.onopen = () => {
            if (socket !== current) return;
            opened = true;
            attempts = 0;
            setConnected(true);
        };
        current.onmessage = event => {
            if (socket !== current) return;
            const frame = parseMessengerFrame(event.data);
            if (frame === 'ping') current.send(JSON.stringify({ type: 'pong' }));
            else if (frame) options.onSignal(frame);
        };
        current.onerror = () => {};
        current.onclose = event => {
            if (socket !== current) return;
            socket = null;
            setConnected(false);
            void afterClose(event.code, opened);
        };
    }

    async function afterClose(code: number, opened: boolean) {
        if (!active) return;
        if (code === CLOSE.suspended) {
            // 정지 회원은 다시 연결하지 않는다. 폴링은 각 화면의 저장소가 계속한다.
            active = false;
            return;
        }
        if (code === CLOSE.tokenExpired || !opened) {
            const session = await options.checkSession();
            if (!active || socket) return;
            if (session === 'expired') {
                active = false;
                options.onSessionExpired();
                return;
            }
            // 토큰을 갱신했으면 바로 다시 연결한다. 핸드셰이크 거부나 네트워크 실패는 간격을 늘린다.
            if (code === CLOSE.tokenExpired && opened && session === 'active') {
                timer = setTimeout(open, 0);
                return;
            }
        }
        reconnectLater();
    }

    function reconnectLater() {
        if (!active) return;
        const cap = Math.min(RECONNECT_MAX, RECONNECT_BASE * 2 ** attempts);
        attempts += 1;
        clearTimeout(timer);
        timer = setTimeout(open, cap / 2 + random() * (cap / 2));
    }

    return {
        start() {
            if (active) return;
            active = true;
            attempts = 0;
            open();
        },
        stop() {
            active = false;
            clearTimeout(timer);
            timer = undefined;
            const current = socket;
            socket = null;
            current?.close(CLOSE.normal);
            setConnected(false);
        },
        /** 연결돼 있을 때만 보낸다. 입력 중 표시는 유실을 허용한다. */
        send(frame: object) {
            if (!connected || !socket || socket.readyState !== OPEN) return false;
            socket.send(JSON.stringify(frame));
            return true;
        },
        isConnected: () => connected,
    };
}

/** /api/auth/session을 불러 토큰을 갱신하고 로그인 상태를 확인한다. 연결 실패는 알 수 없음으로 둔다. */
export async function checkMessengerSession(): Promise<SessionState> {
    try {
        const response = await fetch('/api/auth/session', { cache: 'no-store' });
        if (!response.ok) return 'unknown';
        const body = await response.json() as { identity?: unknown };
        return body?.identity ? 'active' : 'expired';
    } catch {
        return 'unknown';
    }
}
