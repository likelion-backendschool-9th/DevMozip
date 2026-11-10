import { createLocalStore, derived, useStore } from '@/lib/localStore';
import { applyMessagingAction, directUnread, loadMessaging, reconcileMessaging as reconcile, saveMessaging, unifiedNotices, unreadNotices } from '@/lib/messaging/messagingService';
import { canViewProfile, getUser } from '@/lib/state/authStore';
import { getProfileState } from '@/lib/state/profileStore';
import { getProjectState, projectClock, projectSources } from '@/lib/state/projectStore';
import { notify } from '@/lib/state/prototypeStore';
import type { MessagingAction, MessagingState } from '@/types/messaging';

// 1:1 쪽지 · 프로젝트 단체 대화방 · 알림 읽음 상태.
const messagingStore = createLocalStore(() => reconcile(loadMessaging(), getProjectState(), getProfileState(), projectClock()), true);

const messagingSources = [...projectSources, messagingStore];

function commit(next: MessagingState) {
    messagingStore.set(next);
    return saveMessaging(next);
}

/** 프로젝트 팀원 · 프로필 변화에 맞춰 대화방과 알림을 다시 맞춘다. 달라진 것이 없으면 저장하지 않는다 */
export function reconcileMessaging() {
    const next = reconcile(messagingStore.get(), getProjectState(), getProfileState(), projectClock());
    if (JSON.stringify(next) !== JSON.stringify(messagingStore.get())) commit(next);
}

/** 처음 불러온 상태를 저장하고 1분마다 reconcileMessaging을 실행한다 — MessagingSync가 호출한다 */
export function startMessagingSync() {
    saveMessaging(messagingStore.get());
    const timer = window.setInterval(reconcileMessaging, 60000);
    return () => window.clearInterval(timer);
}

function act(action: MessagingAction): { ok: boolean; error?: string; conversationId?: string } {
    try {
        const result = applyMessagingAction(messagingStore.get(), action, getUser(), getProfileState(), getProjectState(), projectClock());
        if (JSON.stringify(result.state) === JSON.stringify(messagingStore.get())) return { ok: true, conversationId: result.conversationId };
        const saved = commit(result.state);
        if (!saved) notify('변경했지만 저장 공간을 사용할 수 없어 이번 세션에만 유지됩니다.');
        else if (action.type.startsWith('send-')) notify('Mock 메시지를 보냈습니다.');
        else if (action.type === 'report-user') notify('Mock 사용자 신고를 기록했습니다.');
        return { ok: true, conversationId: result.conversationId };
    } catch (err) {
        const error = err instanceof Error ? err.message : '요청을 처리하지 못했습니다.';
        notify(error);
        return { ok: false, error };
    }
}

/** 현재 사용자에게 온 알림(볼 수 없는 계정의 게시글 알림은 뺀다) */
const notifications = derived(messagingSources, () => {
    const user = getUser();
    const profiles = getProfileState();
    return unifiedNotices(profiles, getProjectState())
        .filter(n => n.recipientId === user?.id)
        .filter(n => {
            const id = /^\/feeds\/([^?#]+)/.exec(n.href)?.[1];
            const post = profiles.posts.find(p => p.id === id);
            return !post || canViewProfile(post.authorId);
        });
});

const messaging = derived(messagingSources, () => {
    const user = getUser();
    const state = messagingStore.get();
    return {
        state,
        notifications: notifications.get(),
        unreadCount: user ? unreadNotices(state, notifications.get(), user.id).length : 0,
        unreadMessages: user ? directUnread(state, user.id) : 0,
        act,
    };
});

export function useMessaging() {
    return useStore(messaging);
}
