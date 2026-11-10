import { readLoginPrompt, clearLoginPrompt } from '@/lib/matching/loginPrompt';
import { AiApiError, aiConditionsKey, analyzeAiMessage, applyAiAnalysis, createAiConversation, getAiHistory, getAiRecommendation, listAiConversations, getAiConversation, syncAiDraft, executeAiRecommendation, deleteAiConversation, toAiConditions, toAiDraftConditions, mergeAiConversation } from '@/lib/matching/aiApi';
import { CHAT_TOTAL_LIMIT, chatMessageLength, sortChatConversations, missingChatRequirements, validateChatRequirements } from '@/lib/matching/matchingChatService';
import { createMockId } from '@/lib/utils/createMockId';
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
import type { AiRecommendation } from '@/types/aiRecommendation';
import type { ChatRequirements, MatchingConversation } from '@/types/matchingChat';

type ChatResult = { ok: boolean; error?: string; conversationId?: string };

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
    if (!getUser()) return;
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
    if (!getUser()) return;
    const current = homeStore.get();
    const chat = structuredClone(current.chat);
    const items = chat.conversations[owner()] || [];
    chat.conversations[owner()] = sortChatConversations([c, ...items.filter(v => v.id !== c.id)]);
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

function newChat(description?: string) {
    if (!getUser()) return;
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
    if (!getUser()) return { ok: false, error: '로그인 후 AI 추천 대화를 이용해 주세요.' };
    const current = homeStore.get();
    const description = current.inputOwner === owner() ? current.input.description.trim() : '';
    if (description.length < 10) return { ok: false, error: '프로젝트를 10자 이상 설명해 주세요.' };
    try {
        const conversation = newMatchingConversation();
        const saved = sendMatchingMessage(conversation, description, { ...conversation.requirements, description });
        const next = structuredClone(current);
        const account = owner();
        next.chat.conversations[account] = sortChatConversations([saved, ...(next.chat.conversations[account] || [])]);
        next.chat.active[account] = saved.id;
        next.input.description = '';
        if (!saveHomeState(next)) return { ok: false, error: '대화를 저장하지 못했습니다. 입력은 유지됩니다. 저장 공간을 확인한 뒤 다시 시도해 주세요.' };
        homeStore.set(next);
        return { ok: true };
    } catch (error) {
        return fail(error);
    }
}

function resumeLoginPrompt(token: string): ChatResult {
    if (!getUser()) return { ok: false };
    const text = readLoginPrompt(token);
    if (!text) return { ok: false };
    const current = homeStore.get();
    const c = newMatchingConversation();
    const saved = sendMatchingMessage(c, text, c.requirements);
    const next = structuredClone(current);
    const account = owner();
    next.chat.conversations[account] = sortChatConversations([saved, ...(next.chat.conversations[account] || [])]);
    next.chat.active[account] = saved.id;
    if (!saveHomeState(next)) return { ok: false, error: '입력을 대화에 저장하지 못했습니다. 저장 공간을 확인해 주세요.' };
    homeStore.set(next);
    clearLoginPrompt();
    return { ok: true };
}

function selectChat(id: string) {
    if (!getUser()) return;
    const current = homeStore.get();
    if ((current.chat.conversations[owner()] || []).some(c => c.id === id)) commit({ ...current, chat: { ...current.chat, active: { ...current.chat.active, [owner()]: id } } });
}

function deleteChat(id: string) {
    if (!getUser()) return;
    const current = homeStore.get();
    const chat = structuredClone(current.chat);
    chat.conversations[owner()] = (chat.conversations[owner()] || []).filter(c => c.id !== id);
    if (chat.active[owner()] === id) chat.active[owner()] = chat.conversations[owner()][0]?.id || '';
    commit({ ...current, chat });
}

function editChat(patch: Partial<ChatRequirements>) {
    if (!getUser()) return;
    const c = activeConversation();
    saveConversation({ ...c, requirements: { ...c.requirements, ...patch }, updatedAt: new Date().toISOString() });
}

function editChatMessage(text: string) {
    if (!getUser()) return;
    saveConversation({ ...activeConversation(), draft: text.slice(0, 1500), updatedAt: new Date().toISOString() });
}

function sendChat(text: string): ChatResult {
    if (!getUser()) return { ok: false, error: '로그인 후 AI 추천 대화를 이용해 주세요.' };
    try {
        const c = activeConversation();
        if (c.pendingAnalysisKey) throw new Error('이전 메시지 분석을 먼저 다시 시도해 주세요.');
        saveConversation(sendMatchingMessage(c, text, c.requirements));
        return { ok: true, conversationId: c.id };
    } catch (e) {
        return fail(e);
    }
}

// One in-flight mutation per account/conversation. Captured account checks prevent late writes after logout.
const syncing = new Set<string>();
const historyRevision = new Map<string, number>();
function accountGuard(account: string) {
    if (!getUser() || owner() !== account) throw new Error('로그인 계정이 변경되었습니다. 다시 시도해 주세요.');
}
function existingChat(id: string) {
    return (homeStore.get().chat.conversations[owner()] || []).find(c => c.id === id);
}
async function loadAiChats() {
    const account = owner();
    if (!getUser()) return;
    const revision = historyRevision.get(account) || 0;
    const remote = await listAiConversations();
    accountGuard(account);
    const details = await Promise.all(remote.map(c => getAiConversation(c.id)));
    accountGuard(account);
    if ((historyRevision.get(account) || 0) !== revision) return;
    const current = homeStore.get();
    const chat = structuredClone(current.chat);
    let items = chat.conversations[account] || [];
    for (const detail of details) {
        const local = items.find(c => c.serverId === detail.id || c.id === detail.clientKey);
        const merged = mergeAiConversation(detail, local);
        items = [merged, ...items.filter(c => c.id !== merged.id)];
    }
    chat.conversations[account] = sortChatConversations(items);
    if (!items.some(c => c.id === chat.active[account])) chat.active[account] = items[0]?.id || '';
    commit({ ...current, chat });
}
function recommendationUnchanged(c: MatchingConversation | null) {
    return !!c?.lastRecommendation && !c.pendingRecommendation
        && aiConditionsKey(c.lastRecommendation.conditions) === aiConditionsKey(toAiConditions(c.requirements));
}
function rememberAiRecommendation(conversationId: number, result: AiRecommendation) {
    if (result.status !== 'SUCCESS' || !getUser()) return;
    const current = homeStore.get();
    const items = current.chat.conversations[owner()] || [];
    const c = items.find(v => v.serverId === conversationId);
    if (!c || c.lastRecommendation && c.lastRecommendation.requestId >= result.requestId) return;
    const chat = structuredClone(current.chat);
    chat.conversations[owner()] = items.map(v => v.id === c.id ? { ...v, lastRecommendation: { requestId: result.requestId, conditions: result.conditions } } : v);
    commit({ ...current, chat });
}
async function analyzeChat() {
    const account = owner();
    accountGuard(account);
    const initial = activeConversation();
    const lock = `${account}:${initial.id}`;
    if (syncing.has(lock)) throw new Error('이전 요청을 처리하고 있습니다.');
    const pending = initial.pendingAnalysisKey
        ? initial.messages.filter(m => m.role === 'user' && m.id === initial.pendingAnalysisKey)
        : initial.messages.filter(m => m.role === 'user' && !m.serverId);
    const message = pending.at(-1);
    if (!message) return;
    syncing.add(lock);
    try {
        let serverId = initial.serverId;
        if (!serverId) {
            const created = await createAiConversation(initial.title);
            accountGuard(account);
            serverId = created.id;
            const current = existingChat(initial.id);
            if (!current) throw new Error('대화가 삭제되었습니다.');
            saveConversation({ ...current, serverId });
        }
        // Sync the complete draft before analysis; missing required selections are allowed here.
        if (!initial.pendingAnalysisKey) {
            validateChatRequirements(initial.requirements);
            const saved = await syncAiDraft({ ...initial, serverId }, toAiDraftConditions(initial.requirements));
            accountGuard(account);
            const latest = existingChat(initial.id);
            if (!latest) throw new Error('대화가 삭제되었습니다.');
            saveConversation(mergeAiConversation(saved, latest));
        }
        // sync already persisted all pending USER messages; only the latest needs analysis.
        const current = existingChat(initial.id);
        if (!current) throw new Error('대화가 삭제되었습니다.');
        saveConversation({ ...current, serverId, pendingAnalysisKey: message.id });
        if (!saveHomeState(homeStore.get())) throw new Error('재시도 정보를 저장할 수 없습니다. 브라우저 저장 공간을 확인해 주세요.');
        const analysis = await analyzeAiMessage(serverId, message.text, message.id);
        accountGuard(account);
        const latest = existingChat(initial.id);
        if (!latest) throw new Error('대화가 삭제되었습니다.');
        saveConversation({ ...applyAiAnalysis(latest, analysis), serverId, pendingAnalysisKey: undefined });
    } finally { syncing.delete(lock); }
}
async function recommendChat() {
    const account = owner();
    accountGuard(account);
    const initial = activeConversation();
    const lock = `${account}:${initial.id}`;
    if (syncing.has(lock)) throw new Error('추천 요청을 처리하고 있습니다.');
    if (!initial.pendingRecommendation) {
        if (recommendationUnchanged(initial)) throw new Error('이미 추천한 조건입니다. 추천 조건을 변경해 주세요.');
        if (chatMessageLength(initial.messages) > CHAT_TOTAL_LIMIT) throw new Error('대화가 10,000자를 초과했습니다. 새 대화를 시작해 주세요.');
        validateChatRequirements(initial.requirements);
        if (missingChatRequirements(initial.requirements).length) throw new Error('관심 분야·경력·기간을 선택해 주세요.');
    }
    syncing.add(lock);
    try {
        // 이력 복원이 끝나기 전에 눌러도 기존 성공 조건으로 새 POST를 만들지 않는다.
        if (!initial.pendingRecommendation && initial.serverId && !initial.lastRecommendation) {
            const history = await getAiHistory(initial.serverId);
            accountGuard(account);
            const success = history.find(v => v.status === 'SUCCESS');
            if (success) {
                const previous = await getAiRecommendation(initial.serverId, success.requestId);
                accountGuard(account);
                rememberAiRecommendation(initial.serverId, previous);
            }
        }
        if (!initial.pendingRecommendation && recommendationUnchanged(existingChat(initial.id) || null)) {
            throw new Error('이미 추천한 조건입니다. 추천 조건을 변경해 주세요.');
        }
        // An uncertain network outcome must reuse the original key and input, including after reload.
        const pending = initial.pendingRecommendation || { key: createMockId(), conditions: toAiConditions(initial.requirements), messageKey: initial.messages.filter(m => m.role === 'user').at(-1)?.id || null };
        saveConversation({ ...initial, pendingRecommendation: pending });
        if (!saveHomeState(homeStore.get())) throw new Error('재시도 정보를 저장할 수 없습니다. 브라우저 저장 공간을 확인해 주세요.');
        const saved = await syncAiDraft(initial, pending.conditions);
        accountGuard(account);
        const current = existingChat(initial.id);
        if (!current) throw new Error('대화가 삭제되었습니다.');
        saveConversation(mergeAiConversation(saved, { ...current, pendingRecommendation: pending }));
        const boundary = pending.messageKey ? saved.messages.find(m => m.clientKey === pending.messageKey || m.id === initial.messages.find(v => v.id === pending.messageKey)?.serverId) : undefined;
        if (pending.messageKey && !boundary) throw new Error('추천에 반영할 메시지 저장을 확인하지 못했습니다. 다시 시도해 주세요.');
        const result = await executeAiRecommendation(saved.id, pending.key, boundary?.id);
        accountGuard(account);
        const latest = existingChat(initial.id);
        if (latest && result.status !== 'PENDING') saveConversation({ ...latest, pendingRecommendation: undefined,
            ...(result.status === 'SUCCESS' ? { lastRecommendation: { requestId: result.requestId, conditions: result.conditions || pending.conditions } } : {}) });
        return result;
    } catch (error) {
        if (getUser() && owner() === account && error instanceof AiApiError && [400, 409].includes(error.status)) {
            const current = existingChat(initial.id);
            if (current) saveConversation({ ...current, pendingRecommendation: undefined });
        }
        throw error;
    } finally { syncing.delete(lock); }
}
async function deleteAiChat(id: string) {
    const account = owner();
    accountGuard(account);
    const c = existingChat(id);
    if (!c) return;
    if (syncing.has(`${account}:${id}`)) throw new Error('추천 처리 후 대화를 삭제해 주세요.');
    const serverId = c.serverId || (c.pendingRecommendation ? (await listAiConversations()).find(v => v.clientKey === c.id)?.id : undefined);
    accountGuard(account);
    if (serverId) {
        try { await deleteAiConversation(serverId); }
        catch (error) { if (!(error instanceof AiApiError && error.status === 404)) throw error; }
    }
    accountGuard(account);
    historyRevision.set(account, (historyRevision.get(account) || 0) + 1);
    deleteChat(id);
}

const home = derived(homeSources, () => {
    const user = getUser();
    const state = homeStore.get();
    const conversations = user ? state.chat.conversations[owner()] || [] : [];
    const conversation = conversations.find(c => c.id === state.chat.active[owner()]) || null;
    const snapshot = user ? state.matching.last[user.id] : null;
    return {
        state: { ...state, input: user && state.inputOwner === owner() ? state.input : structuredClone(freshHomeState().input), chat: user ? { conversations: { [owner()]: conversations }, active: { [owner()]: state.chat.active[owner()] } } : { conversations: {}, active: {} }, role: user?.role || 'guest', following: user ? (getProfileState().following[user.id] || []) : [] },
        usage: matchingUsage(state.matching, user, clockStore.get()),
        result: snapshot && snapshot.inputKey === inputKey(state.input) ? { ...snapshot, result: { ...snapshot.result, developers: snapshot.result.developers.filter(d => canViewProfile(d.id)) } } : null,
        run,
        update: (patch: Partial<HomeState>) => commit({ ...homeStore.get(), ...patch, inputOwner: owner() }),
        reset: () => commit({ ...homeStore.get(), input: structuredClone(freshHomeState().input), inputOwner: owner() }),
        conversations,
        conversation,
        newChat,
        startHomeChat,
        resumeLoginPrompt,
        selectChat,
        deleteChat,
        editChat,
        sendChat,
        analyzeChat,
        editChatMessage,
        loadAiChats,
        recommendChat,
        recommendationUnchanged: recommendationUnchanged(conversation),
        rememberAiRecommendation,
        deleteAiChat,
    };
});

export function useHome() {
    return useStore(home);
}
