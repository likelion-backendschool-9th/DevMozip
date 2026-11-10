import type { DirectChatDetail, DirectChatList, DirectChatMessage, DirectChatPeer } from '@/types/messaging';

export const directChatPeerFixture: DirectChatPeer = {
    tag: 'sample_dev', nickname: '샘플개발자', profileImage: null,
    jobCategories: ['백엔드 개발자'], collaborationStatus: 'AVAILABLE',
};
export const directChatMessageFixture: DirectChatMessage = {
    id: 340, body: '안녕하세요', mine: false, createdAt: '2026-11-06T10:15:00+09:00',
};
export const directChatListFixture: DirectChatList = {
    items: [{ id: 12, peer: directChatPeerFixture, lastMessage: directChatMessageFixture, unreadCount: 2 }],
    nextCursor: null,
};
export const directChatDetailFixture: DirectChatDetail = {
    id: 12, peer: directChatPeerFixture, canSend: true, peerLastReadMessageId: 339,
    messages: [directChatMessageFixture], nextBefore: null,
};
