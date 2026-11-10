'use client';
import { useParams } from 'next/navigation';
import { useQueryState as useSearchParams } from '@/lib/navigation';
import { validChatId } from '@/lib/messaging/directChatApi';
import { directId } from '@/lib/messaging/messagingService';
import { useAuth } from '@/lib/state/authStore';
import { useMessaging } from '@/lib/state/messagingStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { useProfiles } from '@/lib/state/profileStore';
import type { DirectConversation } from '@/types/messaging';

/**
 * Mock 토글 ON의 열린 1:1 대화 — /messages/chats/{대화 ID}이면 그 대화, ?userId=로 들어오면 그 사용자와의 대화(없으면 새 대화),
 * 둘 다 없으면 가장 최근 대화.
 */
export function useMockActiveConversation() {
    const { user } = useAuth();
    const { state: profiles } = useProfiles();
    const { state } = useMessaging();
    const [params] = useSearchParams();
    const { conversationId } = useParams<{ conversationId?: string }>();
    const conversations = user ? state.conversations.filter(c => c.participants.includes(user.id)).sort((a, b) => (b.messages.at(-1)?.createdAt || '').localeCompare(a.messages.at(-1)?.createdAt || '')) : [];
    const requested = params.get('userId');
    const target = requested && requested !== user?.id && Object.hasOwn(profiles.profiles, requested) ? requested : null;
    const selected = conversationId ? decodeURIComponent(conversationId) : null;
    const active: DirectConversation | undefined = requested && !target
        ? undefined
        : target && user
            ? state.conversations.find(c => c.id === directId(user.id, target)) || { id: directId(user.id, target), participants: [user.id, target], messages: [] }
            : selected
                ? conversations.find(c => c.id === selected)
                : conversations[0];
    return { active, requested, selected };
}

/**
 * Mock 토글 OFF의 열린 1:1 대화 — 주소의 대화방 ID만 쓴다.
 * ?userId=는 Mock 회원 ID라서 서버 대화로 열지 않는다.
 */
export function useServerActiveChat() {
    const [params] = useSearchParams();
    const { conversationId } = useParams<{ conversationId?: string }>();
    const requested = params.get('userId');
    const selected = conversationId ? decodeURIComponent(conversationId) : null;
    const id = !requested && selected && validChatId(selected) ? Number(selected) : null;
    return { id, requested, selected };
}

/** 지금 열린 대화의 ID. 사이드바가 있는 layout이 쓴다. 대화 내용은 useConversationPane이 맡는다. */
export function useActiveConversationId() {
    const { enabled } = useMockVisibility();
    const mock = useMockActiveConversation();
    const server = useServerActiveChat();
    return enabled ? mock.active?.id : server.id === null ? undefined : String(server.id);
}
