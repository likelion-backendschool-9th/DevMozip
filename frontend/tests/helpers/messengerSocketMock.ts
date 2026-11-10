import type { MessengerRoomType, MessengerSignal } from '@/lib/messaging/messengerSocket';

/**
 * '@/lib/state/messengerSocketStore'를 대신한다. 신호와 연결 상태를 테스트가 직접 보낸다.
 * vi.mock('@/lib/state/messengerSocketStore', async () => (await import('@/tests/helpers/messengerSocketMock')).messengerSocketStoreMock());
 */
const signalListeners = new Set<(signal: MessengerSignal) => void>();
const connectionListeners = new Set<(connected: boolean) => void>();
const expiryListeners = new Set<() => void>();

export const socketMock = {
    connected: false,
    /** sendMessengerTyping의 결과 */
    sendSucceeds: true,
    typingSent: [] as Array<{ roomType: MessengerRoomType; roomId: number }>,
    started: 0,
    stopped: 0,
};

const subscribe = <T>(listeners: Set<T>, listener: T) => {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
};

export function messengerSocketStoreMock() {
    return {
        startMessengerSocket: () => {
            socketMock.started += 1;
            return () => {
                socketMock.stopped += 1;
            };
        },
        onMessengerSignal: (listener: (signal: MessengerSignal) => void) => subscribe(signalListeners, listener),
        onMessengerConnection: (listener: (connected: boolean) => void) => subscribe(connectionListeners, listener),
        onMessengerSessionExpired: (listener: () => void) => subscribe(expiryListeners, listener),
        messengerConnected: () => socketMock.connected,
        sendMessengerTyping: (roomType: MessengerRoomType, roomId: number) => {
            if (!socketMock.sendSucceeds) return false;
            socketMock.typingSent.push({ roomType, roomId });
            return true;
        },
    };
}

export const emitSignal = (signal: MessengerSignal) => signalListeners.forEach(listener => listener(signal));

export function emitConnection(connected: boolean) {
    socketMock.connected = connected;
    connectionListeners.forEach(listener => listener(connected));
}

export const emitSessionExpired = () => expiryListeners.forEach(listener => listener());

export function resetSocketMock() {
    socketMock.connected = false;
    socketMock.sendSucceeds = true;
    socketMock.typingSent = [];
    socketMock.started = 0;
    socketMock.stopped = 0;
}

export const subscriberCount = () => signalListeners.size + connectionListeners.size + expiryListeners.size;
