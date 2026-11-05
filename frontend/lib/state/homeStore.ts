import { createLocalStore, derived, useStore, type Source } from '@/lib/localStore';
import { freshHomeState, loadHomeState, saveHomeState } from '@/lib/matching/homeService';
import { consumeMatching, inputKey, matchingUsage } from '@/lib/matching/matchingService';
import { newMatchingConversation, sendMatchingMessage } from '@/lib/matching/matchingChatService';
import { temperature } from '@/lib/profiles/profileService';
import { allProjects } from '@/lib/projects/projectService';
import { canViewProfile, getPublicUsers, getUser } from '@/lib/state/authStore';
import { getProfileState } from '@/lib/state/profileStore';
import { getProjectState, projectSources } from '@/lib/state/projectStore';
import { notify } from '@/lib/state/prototypeStore';
import type { HomeState } from '@/types/home';
import type { ChatRequirements, MatchingConversation } from '@/types/matchingChat';

type ChatResult = { ok: boolean; error?: string };

// 메인 추천 입력 · AI 추천 대화 기록 · 월별 추천 사용량.
const homeStore = createLocalStore(loadHomeState, true);

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

/** 추천 후보 — 관리자와 볼 수 없는 계정은 뺀다 */
function candidates() {
    const excluded = getPublicUsers().filter(u => u.role === 'admin').map(u => u.id);
    const profileState = getProfileState();
    return Object.values(profileState.profiles)
        .filter(p => !excluded.includes(p.id) && canViewProfile(p.id))
        .map(p => ({ id: p.id, name: p.nickname, role: p.job, jobs: p.jobs, career: p.career, bio: p.bio, stacks: p.stacks, interests: p.interests, color: p.color, temperature: temperature(profileState.reviews, p.id), available: p.available }));
}

const owner = () => getUser()?.id || 'guest';

function activeConversation() {
    const current = homeStore.get();
    return (current.chat.conversations[owner()] || []).find(v => v.id === current.chat.active[owner()]) || newMatchingConversation();
}

function saveConversation(c: MatchingConversation, matching = homeStore.get().matching) {
    const current = homeStore.get();
    const chat = structuredClone(current.chat);
    const items = chat.conversations[owner()] || [];
    chat.conversations[owner()] = [c, ...items.filter(v => v.id !== c.id)];
    chat.active[owner()] = c.id;
    commit({ ...current, chat, matching });
}

function fail(error: unknown): ChatResult {
    const message = error instanceof Error ? error.message : '대화를 저장하지 못했습니다.';
    notify(message);
    return { ok: false, error: message };
}

function run(): ChatResult {
    try {
        const current = homeStore.get();
        const next = consumeMatching(current.matching, getUser(), current.input, candidates(), allProjects(getProjectState()));
        commit({ ...current, matching: next.state });
        return { ok: true };
    } catch (error) {
        const message = error instanceof Error ? error.message : '추천하지 못했습니다.';
        notify(message);
        return { ok: false, error: message };
    }
}

function newChat(description?: string) {
    try {
        const c = newMatchingConversation();
        if (description) {
            const next = sendMatchingMessage(c, description, c.requirements, homeStore.get().matching, getUser(), candidates(), allProjects(getProjectState()));
            saveConversation(next.conversation, next.matching);
        } else saveConversation(c);
    } catch (e) {
        fail(e);
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
    saveConversation({ ...c, requirements: { ...c.requirements, ...patch } });
}

function sendChat(text: string): ChatResult {
    try {
        const c = activeConversation();
        const next = sendMatchingMessage(c, text, c.requirements, homeStore.get().matching, getUser(), candidates(), allProjects(getProjectState()));
        saveConversation(next.conversation, next.matching);
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
        selectChat,
        deleteChat,
        editChat,
        sendChat,
    };
});

export function useHome() {
    return useStore(home);
}
