import { createLocalStore, derived, useStore, type Source } from '@/lib/localStore';
import { freshHomeState, loadHomeState, saveHomeState } from '@/lib/matching/homeService';
import { inputKey, matchingUsage } from '@/lib/matching/matchingService';
import { newMatchingConversation, sendMatchingMessage } from '@/lib/matching/matchingChatService';
import { canViewProfile, getUser } from '@/lib/state/authStore';
import { getProfileState } from '@/lib/state/profileStore';
import { projectSources } from '@/lib/state/projectStore';
import { notify } from '@/lib/state/prototypeStore';
import { demoAccounts } from '@/mocks/auth';
import type { HomeState } from '@/types/home';
import type { ChatRequirements, MatchingConversation } from '@/types/matchingChat';

type ChatResult = { ok: boolean; error?: string };

// 메인 추천 입력 · AI 추천 대화 기록 · 월별 추천 사용량.
const homeStore = createLocalStore(loadHomeState);

// 월 경계와 탭 복귀 시 현재 한국 시각의 한도를 다시 표시합니다. 처음 구독할 때 시계를 시작합니다.
const clockStore = createLocalStore(() => new Date());
let ticking = false;
const clockSource: Source = {
    version: clockStore.version,
    subscribe: listener => {
        if (!ticking && typeof window !== 'undefined') {
            ticking = true;
            const refresh = () => clockStore.set(new Date());
            window.setInterval(refresh, 30000);
            window.addEventListener('focus', refresh);
        }
        return clockStore.subscribe(listener);
    },
};

const homeSources = [...projectSources, homeStore, clockSource];

function commit(next: HomeState) {
    homeStore.set(next);
    if (!saveHomeState(next)) notify('저장 공간을 사용할 수 없어 대화와 추천 사용량이 이번 세션에만 유지됩니다.');
}

// 공통 화면의 Mock 프로필 ID가 같아도 실제 로그인 이메일이 다르면 초안을 분리한다.
const owner = () => {
    const user = getUser();
    if (!user) return 'guest';
    return demoAccounts.some(account => account.id === user.id && account.email === user.email)
        ? user.id : `account:${user.email.toLowerCase()}`;
};

function activeConversation() {
    const current = homeStore.get();
    return (current.chat.conversations[owner()] || []).find(v => v.id === current.chat.active[owner()]) || newMatchingConversation();
}

function saveConversation(c: MatchingConversation) {
    const current = homeStore.get();
    const chat = structuredClone(current.chat);
    const items = chat.conversations[owner()] || [];
    chat.conversations[owner()] = [c, ...items.filter(v => v.id !== c.id)]
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id));
    chat.active[owner()] = c.id;
    commit({ ...current, chat });
}

function fail(error: unknown): ChatResult {
    const message = error instanceof Error ? error.message : '대화를 저장하지 못했습니다.';
    notify(message);
    return { ok: false, error: message };
}

function run(): ChatResult {
    return { ok: false, error: '추천 실행은 아직 준비 중입니다. 대화와 조건은 이 브라우저에 보관됩니다.' };
}

function prepareChatLogin(): ChatResult {
    return saveHomeState(homeStore.get()) ? { ok: true }
        : { ok: false, error: '초안을 저장하지 못해 로그인 이동을 멈췄습니다. 입력은 현재 화면에 유지됩니다. 저장 공간을 확인해 주세요.' };
}

// 사용자 확인 후 활성 guest 초안 하나만 이동한다. 저장 실패 시 원본을 유지한다.
function resumeGuestChat(): ChatResult {
    if (!getUser()) return { ok: false, error: '로그인 후 초안을 이어받아 주세요.' };
    const current = homeStore.get();
    const guest = (current.chat.conversations.guest || []).find(c => c.id === current.chat.active.guest);
    if (!guest) return { ok: false, error: '이어받을 비로그인 초안이 없습니다.' };
    const next = structuredClone(current);
    const account = owner();
    const items = next.chat.conversations[account] || [];
    const moved = { ...guest, id: items.some(c => c.id === guest.id) ? newMatchingConversation().id : guest.id };
    next.chat.conversations[account] = [moved, ...items].sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id));
    next.chat.active[account] = moved.id;
    next.chat.conversations.guest = (next.chat.conversations.guest || []).filter(c => c.id !== guest.id);
    next.chat.active.guest = next.chat.conversations.guest[0]?.id || '';
    if (!saveHomeState(next)) return { ok: false, error: '초안을 저장하지 못했습니다. 비로그인 원본을 유지했습니다. 저장 공간을 확인한 뒤 다시 시도해 주세요.' };
    homeStore.set(next);
    return { ok: true };
}

function newChat(description?: string) {
    try {
        const c = newMatchingConversation();
        if (description) {
            saveConversation(sendMatchingMessage(c, description, { ...c.requirements, description: description.trim() }));
        } else saveConversation(c);
    } catch (e) {
        fail(e);
    }
}

// 메인 초안은 대화와 함께 저장한 뒤 소비한다. 연속 호출은 최신 입력을 확인한다.
function startHomeChat(): ChatResult {
    const current = homeStore.get();
    const description = current.input.description.trim();
    if (description.length < 10) return { ok: false, error: '프로젝트를 10자 이상 설명해 주세요.' };
    try {
        const conversation = newMatchingConversation();
        const saved = sendMatchingMessage(conversation, description, { ...conversation.requirements, description });
        const next = structuredClone(current);
        const account = owner();
        next.chat.conversations[account] = [saved, ...(next.chat.conversations[account] || [])]
            .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id));
        next.chat.active[account] = saved.id;
        next.input.description = '';
        if (!saveHomeState(next)) return { ok: false, error: '대화를 저장하지 못했습니다. 입력은 유지됩니다. 저장 공간을 확인한 뒤 다시 시도해 주세요.' };
        homeStore.set(next);
        return { ok: true };
    } catch (error) {
        return fail(error);
    }
}

function selectChat(id: string) {
    const current = homeStore.get();
    if ((current.chat.conversations[owner()] || []).some(c => c.id === id)) commit({ ...current, chat: { ...current.chat, active: { ...current.chat.active, [owner()]: id } } });
}

function deleteChat(id: string) {
    const current = homeStore.get();
    const chat = structuredClone(current.chat);
    chat.conversations[owner()] = (chat.conversations[owner()] || []).filter(c => c.id !== id);
    if (chat.active[owner()] === id) chat.active[owner()] = chat.conversations[owner()][0]?.id || '';
    commit({ ...current, chat });
}

function editChat(patch: Partial<ChatRequirements>) {
    const c = activeConversation();
    saveConversation({ ...c, requirements: { ...c.requirements, ...patch }, updatedAt: new Date().toISOString() });
}

function editChatMessage(text: string) {
    saveConversation({ ...activeConversation(), draft: text.slice(0, 1500), updatedAt: new Date().toISOString() });
}

function sendChat(text: string): ChatResult {
    try {
        const c = activeConversation();
        saveConversation(sendMatchingMessage(c, text, c.requirements));
        return { ok: true };
    } catch (e) {
        return fail(e);
    }
}

const home = derived(homeSources, () => {
    const user = getUser();
    const state = homeStore.get();
    const conversations = state.chat.conversations[owner()] || [];
    const conversation = conversations.find(c => c.id === state.chat.active[owner()]) || null;
    const snapshot = user ? state.matching.last[user.id] : null;
    return {
        state: { ...state, role: user?.role || 'guest', following: user ? (getProfileState().following[user.id] || []) : [] },
        usage: matchingUsage(state.matching, user, clockStore.get()),
        result: snapshot && snapshot.inputKey === inputKey(state.input) ? { ...snapshot, result: { ...snapshot.result, developers: snapshot.result.developers.filter(d => canViewProfile(d.id)) } } : null,
        run,
        update: (patch: Partial<HomeState>) => commit({ ...homeStore.get(), ...patch }),
        reset: () => commit({ ...homeStore.get(), input: structuredClone(freshHomeState().input) }),
        conversations,
        conversation,
        newChat,
        startHomeChat,
        selectChat,
        deleteChat,
        editChat,
        sendChat,
        editChatMessage,
        guestDraft: user ? (state.chat.conversations.guest || []).find(c => c.id === state.chat.active.guest) || null : null,
        resumeGuestChat,
        prepareChatLogin,
    };
});

export function useHome() {
    return useStore(home);
}
