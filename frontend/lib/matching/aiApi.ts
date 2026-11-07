import type { AiConditions, AiConversationDetail, AiConversationSummary, AiRecommendation, AiRequestSummary } from '@/types/aiRecommendation';
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
export function fromAiConditions(c: Partial<AiConditions>): ChatRequirements {
    return { ...structuredClone(emptyChatRequirements), interests: c.interests || [], stacks: c.stacks || [],
        roles: c.roles || [], career: c.career || '', duration: c.durationMonths ? `${c.durationMonths}개월` : '' };
}
export const listAiConversations = () => request<AiConversationSummary[]>('');
export const getAiConversation = (id: number) => request<AiConversationDetail>(`/${id}`);
export const getAiHistory = (id: number) => request<AiRequestSummary[]>(`/${id}/recommendations/history`);
export const getAiRecommendation = (id: number, requestId?: number) => request<AiRecommendation>(`/${id}/recommendations${requestId ? `?requestId=${requestId}` : ''}`);
export const deleteAiConversation = (id: number) => request<void>(`/${id}`, { method: 'DELETE' });
export function syncAiDraft(c: MatchingConversation, conditions: AiConditions) {
    return request<AiConversationDetail>('/sync', { method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ conversationId: c.serverId, clientKey: c.id, title: c.title, conditions,
            messages: c.messages.filter(m => m.role === 'user' && !m.serverId).map(m => ({ clientKey: m.id, content: m.text })) }) });
}
export function executeAiRecommendation(id: number, key: string) {
    return request<AiRecommendation>(`/${id}/recommendations`, { method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': key }, body: '{}' });
}

// Keep local unsent edits and match persisted messages by their stable client key.
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
