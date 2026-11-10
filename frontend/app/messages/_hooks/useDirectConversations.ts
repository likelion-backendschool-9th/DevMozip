'use client';
import { useEffect } from 'react';
import { directChatHref, directPeerName } from '@/lib/messaging/directChatView';
import { profileImageUrl } from '@/lib/profiles/profileDetail';
import { useAuth } from '@/lib/state/authStore';
import { loadMoreDirectChats, setDirectChatQuery, useDirectChats, watchDirectChatList } from '@/lib/state/directChatStore';
import { useMessaging } from '@/lib/state/messagingStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { useProfiles } from '@/lib/state/profileStore';

const SEARCH_DELAY = 300;

export interface DirectConversationItem {
    id: string;
    href: string;
    name: string;
    color: string;
    image?: string;
    preview: string;
    unread: number;
}

/**
 * 사이드바 1:1 탭의 목록. Mock과 서버가 같은 형태로 채운다.
 * Mock은 브라우저에서 거르고, 서버는 입력이 멈춘 뒤 q로 조회한다.
 */
export function useDirectConversations(query: string) {
    const { enabled } = useMockVisibility();
    const { user } = useAuth();
    const { state: profiles } = useProfiles();
    const { state: messaging } = useMessaging();
    const { list } = useDirectChats();

    useEffect(() => {
        if (!enabled) return watchDirectChatList();
    }, [enabled]);

    useEffect(() => {
        if (enabled) return;
        const timer = setTimeout(() => setDirectChatQuery(query), SEARCH_DELAY);
        return () => clearTimeout(timer);
    }, [enabled, query]);

    if (!enabled) {
        const items: DirectConversationItem[] = list.items.map(item => ({
            id: String(item.id),
            href: directChatHref(item.id),
            name: directPeerName(item.peer),
            color: 'lavender',
            image: profileImageUrl(item.peer.profileImage),
            preview: item.lastMessage.body,
            unread: item.unreadCount,
        }));
        return { items, status: list.status, hasMore: list.nextCursor !== null, loadingMore: list.loadingMore, loadMore: () => void loadMoreDirectChats() };
    }

    const q = query.trim().toLowerCase();
    const items: DirectConversationItem[] = !user ? [] : messaging.conversations
        .filter(c => c.participants.includes(user.id))
        .sort((a, b) => (b.messages.at(-1)?.createdAt || '').localeCompare(a.messages.at(-1)?.createdAt || ''))
        .map(c => {
            const peer = profiles.profiles[c.participants.find(id => id !== user.id)!];
            return {
                id: c.id,
                href: `/messages/chats/${encodeURIComponent(c.id)}`,
                name: peer?.nickname || '사용자',
                color: peer?.color || 'lavender',
                preview: c.messages.at(-1)?.body || '대화를 시작하세요.',
                unread: c.messages.filter(m => m.senderId !== user.id && !m.readBy.includes(user.id)).length,
            };
        })
        .filter(item => (item.name === '사용자' ? '' : item.name).toLowerCase().includes(q));
    return { items, status: 'ready' as const, hasMore: false, loadingMore: false, loadMore: () => {} };
}
