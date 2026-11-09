import type { AiAnalysisResponse, AiConditions, AiDraftConditions, AiConversationDetail, AiConversationSummary, AiRecommendation, AiRequestSummary } from '@/types/aiRecommendation';
import type { ChatRequirements, MatchingConversation } from '@/types/matchingChat';
import { emptyChatRequirements } from '@/mocks/matchingChat';

export class AiApiError extends Error {
    constructor(public status: number, message: string) { super(message); }
}
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`/api/ai/conversations${path}`, { ...init, cache: 'no-store' });
    const body = response.status === 204 ? null : await response.json();
    if (!response.ok && !(response.status === 500 && body?.status === 'FAILED')) {
        throw new AiApiError(response.status, body?.message || '추천 요청을 완료하지 못했습니다.');
    }
    return body as T;
}
export function toAiConditions(r: ChatRequirements): AiConditions {
    return { interests: r.interests, stacks: r.stacks, roles: r.roles, career: r.career, durationMonths: Number(r.duration.replace('개월', '')) };
}
export function toAiDraftConditions(r: ChatRequirements): AiDraftConditions {
    return { interests: r.interests, stacks: r.stacks, roles: r.roles,
        ...(r.career ? { career: r.career } : {}),
        ...(r.duration ? { durationMonths: Number(r.duration.replace('개월', '')) } : {}) };
}
// 선택 순서는 추천 조건의 변경이 아니다. 기술명 대소문자·앞뒤 공백도 비교 시 정규화한다.
export function aiConditionsKey(c: AiConditions): string {
    return JSON.stringify({ interests: [...new Set(c.interests.map(v => v.trim()))].sort(),
        stacks: [...new Set((c.stacks || []).map(v => v.trim().toLowerCase()))].sort(),
        roles: (c.roles || []).map(v => [v.name.trim().toLowerCase(), v.count]).sort((a, b) => String(a[0]).localeCompare(String(b[0]))),
        career: c.career, durationMonths: c.durationMonths });
}
export function fromAiConditions(c: Partial<AiConditions>): ChatRequirements {
    return { ...structuredClone(emptyChatRequirements), interests: c.interests || [], stacks: c.stacks || [],
        roles: c.roles || [], career: c.career || '', duration: c.durationMonths ? `${c.durationMonths}개월` : '' };
}
export const listAiConversations = () => request<AiConversationSummary[]>('');
export const createAiConversation = (title: string) => request<AiConversationSummary>('', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ title }) });
export const analyzeAiMessage = (id: number, content: string, clientKey: string) => request<AiAnalysisResponse>(`/${id}/messages`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content, clientKey, analyze: true }) });
export const getAiConversation = (id: number) => request<AiConversationDetail>(`/${id}`);
export const getAiHistory = (id: number) => request<AiRequestSummary[]>(`/${id}/recommendations/history`);
export const getAiRecommendation = (id: number, requestId?: number) => request<AiRecommendation>(`/${id}/recommendations${requestId ? `?requestId=${requestId}` : ''}`);
export const deleteAiConversation = (id: number) => request<void>(`/${id}`, { method: 'DELETE' });
export function syncAiDraft(c: MatchingConversation, conditions: AiDraftConditions) {
    return request<AiConversationDetail>('/sync', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ conversationId: c.serverId, clientKey: c.id, title: c.title, conditions,
            messages: c.messages.filter(m => m.role === 'user' && !m.serverId).map(m => ({ clientKey: m.id, content: m.text })) }) });
}
export function executeAiRecommendation(id: number, key: string, messageId?: number) {
    return request<AiRecommendation>(`/${id}/recommendations`, { method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key }, body: JSON.stringify(messageId ? { messageId } : {}) });
}

export function applyAiAnalysis(local: MatchingConversation, response: AiAnalysisResponse): MatchingConversation {
    const user = response.userMessage;
    const assistant = response.assistantMessage;
    const messages = local.messages.map(m => m.id === user.clientKey || m.serverId === user.id ? { ...m, serverId: user.id, text: user.content, createdAt: user.createdAt } : m);
    if (!messages.some(m => m.serverId === assistant.id)) {
        const index = messages.findIndex(m => m.serverId === user.id);
        messages.splice(index < 0 ? messages.length : index + 1, 0, { id: assistant.clientKey || `server-message:${assistant.id}`, serverId: assistant.id, role: 'assistant', text: assistant.content, createdAt: assistant.createdAt });
    }
    const remote = response.currentConditions || {};
    const structured = fromAiConditions(remote);
    const requirements = { ...local.requirements };
    // CLEAR removes a condition key, while its provenance remains in sources.
    const analysis = response.analysis as { sources?: Record<string, unknown> } | null;
    const sources = analysis?.sources || {};
    if (Array.isArray(remote.interests)) requirements.interests = structured.interests;
    if (Array.isArray(remote.stacks)) requirements.stacks = structured.stacks;
    if (Array.isArray(remote.roles)) requirements.roles = structured.roles;
    if (typeof remote.career === 'string') requirements.career = structured.career;
    if (typeof remote.durationMonths === 'number') requirements.duration = structured.duration;
    if ('interests' in sources && !('interests' in remote)) requirements.interests = [];
    if ('stacks' in sources && !('stacks' in remote)) requirements.stacks = [];
    if ('roles' in sources && !('roles' in remote)) requirements.roles = [];
    if ('career' in sources && !('career' in remote)) requirements.career = '';
    if ('durationMonths' in sources && !('durationMonths' in remote)) requirements.duration = '';
    return { ...local, messages, requirements };
}
export function mergeAiConversation(remote: AiConversationDetail, local?: MatchingConversation): MatchingConversation {
    const messages = remote.messages.map(m => {
        const previous = local?.messages.find(v => v.serverId === m.id || v.id === m.clientKey);
        return { ...previous, id: previous?.id || m.clientKey || `server-message:${m.id}`, serverId: m.id,
            role: m.role === 'USER' ? 'user' as const : 'assistant' as const, text: m.content, createdAt: m.createdAt };
    });
    const pending = local?.messages.filter(m => !messages.some(v => v.id === m.id || v.serverId === m.serverId && m.serverId)) || [];
    return { ...local, id: local?.id || remote.clientKey || `server:${remote.id}`, serverId: remote.id,
        title: remote.title || '새 대화', createdAt: remote.createdAt, updatedAt: remote.updatedAt,
        requirements: local?.requirements || fromAiConditions(remote.currentConditions), messages: [...messages, ...pending] };
}

export async function getAiPublicProject(id: number, signal: AbortSignal): Promise<import('@/types/aiRecommendation').AiPublicProject> {
    const response = await fetch(`/api/ai/projects/${id}`, { cache: 'no-store', signal });
    if (!response.ok) throw new AiApiError(response.status, '프로젝트 정보를 불러올 수 없습니다.');
    return response.json();
}
