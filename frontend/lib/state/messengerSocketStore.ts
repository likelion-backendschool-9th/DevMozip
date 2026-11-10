import {
    checkMessengerSession, createMessengerSocket, messengerSocketUrl, type MessengerRoomType, type MessengerSignal,
} from '@/lib/messaging/messengerSocket';

// 탭마다 연결 하나를 두고 1:1과 프로젝트 채팅의 저장소가 함께 구독한다. 연결은 MessengerSocketSync가 열고 닫는다.
type Listener<T> = (value: T) => void;

const signalListeners = new Set<Listener<MessengerSignal>>();
const connectionListeners = new Set<Listener<boolean>>();
const expiryListeners = new Set<() => void>();

const subscribe = <T>(listeners: Set<T>, listener: T) => {
    listeners.add(listener);
    return () => {
        listeners.delete(listener);
    };
};

const socket = createMessengerSocket({
    // NEXT_PUBLIC_ 값은 빌드할 때 이 자리에 들어간다. 변수 이름을 바꿔 읽으면 들어가지 않는다.
    url: () => (typeof window === 'undefined' ? null : messengerSocketUrl(window.location, process.env.NEXT_PUBLIC_MESSENGER_WS_PORT)),
    checkSession: checkMessengerSession,
    onSignal: signal => signalListeners.forEach(listener => listener(signal)),
    onConnectionChange: connected => connectionListeners.forEach(listener => listener(connected)),
    onSessionExpired: () => expiryListeners.forEach(listener => listener()),
});

/** 연결을 연다. 정리 함수는 연결을 닫는다. */
export function startMessengerSocket() {
    socket.start();
    return () => socket.stop();
}

export const onMessengerSignal = (listener: Listener<MessengerSignal>) => subscribe(signalListeners, listener);
export const onMessengerConnection = (listener: Listener<boolean>) => subscribe(connectionListeners, listener);
export const onMessengerSessionExpired = (listener: () => void) => subscribe(expiryListeners, listener);
export const messengerConnected = () => socket.isConnected();

/** 입력 중 신호를 보낸다. 연결이 없으면 보내지 않고 false를 돌려준다. */
export function sendMessengerTyping(roomType: MessengerRoomType, roomId: number) {
    return socket.send({ type: 'typing', roomType, roomId });
}
