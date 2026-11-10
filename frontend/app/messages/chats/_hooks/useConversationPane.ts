'use client';
import { useEffect } from 'react';
import { useOwnProfile } from '@/app/(user)/_components/OwnProfileProvider';
import type { ChatHistoryControls, ChatSendResult, ChatTypingControls } from '@/app/messages/_components/ChatWindow';
import type { ServerUserReport } from '@/app/messages/_components/UserReportDialog';
import { DIRECT_PEER_MEMBER, directChatHref, directPeerName, directPeerView, profileHref, toChatMessages } from '@/lib/messaging/directChatView';
import { useNavigation as useNavigate } from '@/lib/navigation';
import { useAuth } from '@/lib/state/authStore';
import {
    loadOlderDirectMessages, notifyDirectChatTyping, openDirectChat, sendOpenDirectMessage, setDirectChatAtBottom, submitUserReport,
    useDirectChats,
} from '@/lib/state/directChatStore';
import { useMessaging } from '@/lib/state/messagingStore';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';
import { useProfiles } from '@/lib/state/profileStore';
import type { ChatMessage } from '@/types/messaging';
import { useMockActiveConversation, useServerActiveChat } from './useActiveConversation';

/** 대화 영역이 그리는 내용. Mock과 서버가 같은 형태로 채워, 출처 선택을 이 파일에만 둔다. */
export interface ConversationPane {
    conversation: { id: string; members: string[]; messages: ChatMessage[] } | null;
    /** 대화를 그릴 수 없을 때의 제목 */
    emptyTitle: string;
    peer: { name: string; href: string | null; subtitle: string; color: string; image?: string } | null;
    canSend: boolean;
    send: (body: string) => ChatSendResult | Promise<ChatSendResult>;
    /** null이면 신고 버튼을 표시하지 않는다. */
    report: { targetId?: string; server?: ServerUserReport } | null;
    author?: (senderId: string) => { name: string; href: string | null };
    footerText?: string;
    history?: ChatHistoryControls;
    /** 서버 대화에서만 있다. Mock 대화는 입력 중 표시가 없다. */
    typing?: ChatTypingControls;
}

function useMockPane(enabled: boolean): ConversationPane {
    const { user } = useAuth();
    const { state: profiles } = useProfiles();
    const { state, act } = useMessaging();
    const navigate = useNavigate();
    const { active, requested, selected } = useMockActiveConversation();
    const peerId = active?.participants.find(id => id !== user?.id);
    const peer = peerId ? profiles.profiles[peerId] : null;
    const unread = active?.messages.filter(m => m.senderId !== user?.id && !m.readBy.includes(user?.id || '')).length || 0;

    useEffect(() => {
        if (enabled && active && unread && state.conversations.some(c => c.id === active.id))
            act({ type: 'read-direct', conversationId: active.id });
    }, [enabled, active?.id, user?.id, unread]);

    const ready = active && peerId && peer;
    return {
        conversation: ready ? { id: active.id, members: active.participants, messages: active.messages } : null,
        emptyTitle: requested || selected ? '대화를 열 수 없습니다.' : '대화를 선택해 주세요.',
        peer: ready ? {
            name: peer.nickname,
            href: `/users/${peerId}`,
            subtitle: `${peer.job} · ${peer.available ? '협업 가능' : '협업 쉬는 중'}`,
            color: peer.color,
        } : null,
        canSend: true,
        send: body => {
            if (!peerId) return { ok: false };
            const result = act({ type: 'send-direct', targetId: peerId, body, kind: 'text' });
            if (result.ok && result.conversationId) navigate(`/messages/chats/${encodeURIComponent(result.conversationId)}`, { replace: true });
            return result;
        },
        report: ready ? { targetId: peerId } : null,
    };
}

function useServerPane(enabled: boolean): ConversationPane {
    const { user } = useAuth();
    const own = useOwnProfile();
    const navigate = useNavigate();
    const { open, latestChatId } = useDirectChats();
    const { id, requested, selected } = useServerActiveChat();

    useEffect(() => {
        if (enabled && id !== null) return openDirectChat(id);
    }, [enabled, id]);

    // 주소에 대화가 없으면 가장 최근 대화로 옮긴다. 새 메시지로 목록 순서가 바뀌어도 보던 대화가 바뀌지 않는다.
    useEffect(() => {
        if (enabled && !requested && !selected && latestChatId !== null) navigate(directChatHref(latestChatId), { replace: true });
    }, [enabled, requested, selected, latestChatId]);

    const chat = id !== null && open?.id === id ? open : null;
    const peer = chat?.status === 'ready' ? chat.peer : null;
    const emptyTitle = id !== null && (!chat || chat.status === 'loading')
        ? '대화를 불러오고 있어요…'
        : chat?.status === 'error'
            ? '대화를 불러오지 못했어요. 잠시 후 다시 시도합니다.'
            : requested || selected
                ? '대화를 열 수 없습니다.'
                : '대화를 선택해 주세요.';
    if (!chat || !peer || !user) {
        return { conversation: null, emptyTitle, peer: null, canSend: false, send: () => ({ ok: false }), report: null };
    }

    const peerTag = peer.tag;
    return {
        conversation: {
            id: String(chat.id),
            members: [user.id, DIRECT_PEER_MEMBER],
            messages: toChatMessages(chat.messages, user.id, chat.peerLastReadMessageId),
        },
        emptyTitle,
        peer: directPeerView(peer),
        canSend: chat.canSend,
        send: sendOpenDirectMessage,
        // 태그가 없는 상대는 신고 API의 대상 경로를 만들 수 없다.
        report: peerTag ? { server: { name: directPeerName(peer), onSubmit: reason => submitUserReport(peerTag, reason) } } : null,
        author: senderId => senderId === DIRECT_PEER_MEMBER
            ? { name: directPeerName(peer), href: profileHref(peerTag) }
            : { name: own.profile?.nickname || user.nickname, href: profileHref(own.profile?.tag ?? null) },
        footerText: '2,000자까지 보낼 수 있어요.',
        history: {
            hasOlder: chat.nextBefore !== null,
            loadingOlder: chat.loadingOlder,
            onLoadOlder: () => void loadOlderDirectMessages(),
            onBottomChange: setDirectChatAtBottom,
        },
        typing: {
            text: chat.peerTyping ? `${directPeerName(peer)}님이 입력 중이에요…` : null,
            onTyping: () => notifyDirectChatTyping(),
            display: 'bubble',
        },
    };
}

export function useConversationPane(): ConversationPane {
    const { enabled } = useMockVisibility();
    const mock = useMockPane(enabled);
    const server = useServerPane(!enabled);
    return enabled ? mock : server;
}
