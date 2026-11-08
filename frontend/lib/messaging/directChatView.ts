import { collaborationLabel, profileImageUrl } from '@/lib/profiles/profileDetail';
import type { ChatMessage, DirectChatList, DirectChatMessage, DirectChatPeer } from '@/types/messaging';

/** 서버 응답에는 상대의 회원 ID가 없다. 화면의 읽음 계산에 쓸 상대 자리 표시 값이다. */
export const DIRECT_PEER_MEMBER = 'direct-peer';

export const directChatHref = (id: number) => `/messages/chats/${id}`;
export const profileHref = (tag: string | null) => (tag ? `/users/${encodeURIComponent(tag)}` : null);
export const directPeerName = (peer: DirectChatPeer | null) => peer?.nickname || '사용자';

/** 폴링과 전송 응답이 겹쳐도 메시지가 한 번만 남도록 ID로 합치고 오름차순으로 둔다. */
export function mergeDirectMessages(current: DirectChatMessage[], incoming: DirectChatMessage[]) {
    if (!incoming.length) return current;
    const byId = new Map(current.map(message => [message.id, message]));
    for (const message of incoming) byId.set(message.id, message);
    return [...byId.values()].sort((a, b) => a.id - b.id);
}

/** 첫 페이지를 다시 읽은 결과에, 더 불러온 이전 대화를 이어 붙인다. */
export function mergeFirstChatPage(current: DirectChatList, page: DirectChatList): DirectChatList {
    if (page.nextCursor === null) return page;
    const ids = new Set(page.items.map(item => item.id));
    const floor = page.items.at(-1)?.lastMessage.id ?? 0;
    const older = current.items.filter(item => !ids.has(item.id) && item.lastMessage.id < floor);
    return { items: [...page.items, ...older], nextCursor: older.length ? current.nextCursor : page.nextCursor };
}

/** 내가 아직 읽음으로 남기지 않은 상대 메시지가 있는지 판단할 때 쓴다. */
export function lastPeerMessageId(messages: DirectChatMessage[]) {
    return messages.findLast(message => !message.mine)?.id ?? null;
}

/** ChatWindow가 Mock과 같은 형태로 그릴 수 있게 서버 메시지를 바꾼다. */
export function toChatMessages(messages: DirectChatMessage[], myId: string, peerLastReadMessageId: number | null): ChatMessage[] {
    return messages.map(message => ({
        id: String(message.id),
        senderId: message.mine ? myId : DIRECT_PEER_MEMBER,
        body: message.body,
        kind: 'text',
        createdAt: message.createdAt,
        readBy: peerLastReadMessageId !== null && message.id <= peerLastReadMessageId ? [DIRECT_PEER_MEMBER] : [],
    }));
}

export function directPeerView(peer: DirectChatPeer) {
    return {
        name: directPeerName(peer),
        href: profileHref(peer.tag),
        subtitle: `${peer.jobCategories.join(', ') || '직군 미입력'} · ${collaborationLabel(peer.collaborationStatus)}`,
        color: 'lavender',
        image: profileImageUrl(peer.profileImage),
    };
}
