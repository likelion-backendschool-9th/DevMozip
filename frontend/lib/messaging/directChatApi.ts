import { validProfileSlug } from '@/lib/profiles/profileDetail';
import type { DirectChatDetail, DirectChatList, DirectChatMessage, DirectChatPeer, DirectChatSummary } from '@/types/messaging';

export const DIRECT_MESSAGE_MAX_LENGTH = 2000;
const CHATS = '/api/messages/chats';
const RESPONSE_ERROR = '잘못된 메시지 응답';

const identifier = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;
const nullableText = (value: unknown): value is string | null => value === null || typeof value === 'string';
const record = (value: unknown) => {
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(RESPONSE_ERROR);
    return value as Record<string, unknown>;
};
const nullableIdentifier = (value: unknown) => {
    if (value !== null && !identifier(value)) throw new Error(RESPONSE_ERROR);
    return value as number | null;
};
const unreadCount = (value: unknown) => {
    if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 0) throw new Error(RESPONSE_ERROR);
    return value;
};

// 오프셋이 없으면 브라우저 시간대로 해석되므로 거부한다. 밀리초보다 긴 소수는 브라우저마다 해석이 달라 잘라서 넘긴다.
function timestamp(value: unknown) {
    if (typeof value !== 'string' || !/(?:Z|[+-]\d{2}:\d{2})$/.test(value)) throw new Error(RESPONSE_ERROR);
    const trimmed = value.replace(/(\.\d{3})\d+/, '$1');
    if (Number.isNaN(Date.parse(trimmed))) throw new Error(RESPONSE_ERROR);
    return trimmed;
}

// 알 수 없는 추가 필드(이메일 등)는 브라우저 응답에 전달하지 않는다.
function parsePeer(value: unknown): DirectChatPeer {
    const peer = record(value);
    if (!nullableText(peer.tag) || (peer.tag !== null && !validProfileSlug(peer.tag))
        || !nullableText(peer.nickname) || !nullableText(peer.profileImage) || !nullableText(peer.collaborationStatus)
        || !Array.isArray(peer.jobCategories) || !peer.jobCategories.every(job => typeof job === 'string')) throw new Error(RESPONSE_ERROR);
    return { tag: peer.tag, nickname: peer.nickname, profileImage: peer.profileImage,
        jobCategories: peer.jobCategories as string[], collaborationStatus: peer.collaborationStatus };
}

export function parseDirectChatMessage(value: unknown): DirectChatMessage {
    const message = record(value);
    if (!identifier(message.id) || typeof message.body !== 'string' || typeof message.mine !== 'boolean') throw new Error(RESPONSE_ERROR);
    return { id: message.id, body: message.body, mine: message.mine, createdAt: timestamp(message.createdAt) };
}

function parseSummary(value: unknown): DirectChatSummary {
    const item = record(value);
    if (!identifier(item.id)) throw new Error(RESPONSE_ERROR);
    return { id: item.id, peer: parsePeer(item.peer), lastMessage: parseDirectChatMessage(item.lastMessage), unreadCount: unreadCount(item.unreadCount) };
}

export function parseDirectChatList(value: unknown): DirectChatList {
    const list = record(value);
    if (!Array.isArray(list.items)) throw new Error(RESPONSE_ERROR);
    return { items: list.items.map(parseSummary), nextCursor: nullableIdentifier(list.nextCursor) };
}

export function parseDirectChatDetail(value: unknown): DirectChatDetail {
    const detail = record(value);
    if (!identifier(detail.id) || typeof detail.canSend !== 'boolean' || !Array.isArray(detail.messages)) throw new Error(RESPONSE_ERROR);
    return { id: detail.id, peer: parsePeer(detail.peer), canSend: detail.canSend,
        peerLastReadMessageId: nullableIdentifier(detail.peerLastReadMessageId),
        messages: detail.messages.map(parseDirectChatMessage), nextBefore: nullableIdentifier(detail.nextBefore) };
}

export function parseStartedChat(value: unknown): { id: number } {
    const started = record(value);
    if (!identifier(started.id)) throw new Error(RESPONSE_ERROR);
    return { id: started.id };
}

export function parseUnreadCount(value: unknown): { count: number } {
    return { count: unreadCount(record(value).count) };
}

export const validChatId = (id: string) => /^[1-9][0-9]{0,15}$/.test(id) && Number.isSafeInteger(Number(id));

/** 앞뒤 공백을 뺀 본문을 돌려준다. 서버와 같은 길이 규칙에 맞지 않으면 null이다. */
export function directMessageBody(body: unknown): string | null {
    if (typeof body !== 'string') return null;
    const trimmed = body.trim();
    return trimmed.length >= 1 && trimmed.length <= DIRECT_MESSAGE_MAX_LENGTH ? trimmed : null;
}

export class DirectChatRequestError extends Error {
    constructor(public readonly status: number, message: string) { super(message); }
}

async function request(path: string, init: RequestInit = {}) {
    const response = await fetch(path, { ...init, cache: 'no-store' });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new DirectChatRequestError(response.status, typeof body?.message === 'string' ? body.message : '메시지 요청을 완료하지 못했어요.');
    }
    return response;
}

const send = (path: string, method: string, body: unknown) => request(path, {
    method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

function withQuery(path: string, values: Record<string, string | number | undefined>) {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(values)) if (value !== undefined && value !== '') query.set(key, String(value));
    const text = query.toString();
    return text ? `${path}?${text}` : path;
}

export async function startDirectChat(targetSlug: string) {
    if (!validProfileSlug(targetSlug)) throw new DirectChatRequestError(400, '대화 상대를 확인해 주세요.');
    return parseStartedChat(await (await send(CHATS, 'POST', { targetSlug })).json());
}

export async function fetchDirectChats(query: { q?: string; cursor?: number; size?: number } = {}, signal?: AbortSignal) {
    return parseDirectChatList(await (await request(withQuery(CHATS, query), { signal })).json());
}

export async function fetchDirectChat(id: number, query: { before?: number; after?: number; size?: number } = {}, signal?: AbortSignal) {
    return parseDirectChatDetail(await (await request(withQuery(`${CHATS}/${id}`, query), { signal })).json());
}

export async function sendDirectMessage(id: number, body: string) {
    const content = directMessageBody(body);
    if (content === null) throw new DirectChatRequestError(400, '메시지는 1자 이상 2,000자 이하로 입력해 주세요.');
    return parseDirectChatMessage(await (await send(`${CHATS}/${id}`, 'POST', { body: content })).json());
}

export async function markDirectChatRead(id: number, lastReadMessageId: number) {
    await send(`${CHATS}/${id}/read`, 'PATCH', { lastReadMessageId });
}

export async function fetchDirectUnreadCount(signal?: AbortSignal) {
    return parseUnreadCount(await (await request(`${CHATS}/unread-count`, { signal })).json());
}

export async function reportUser(slug: string, reason: string) {
    if (!validProfileSlug(slug)) throw new DirectChatRequestError(400, '신고 대상을 확인해 주세요.');
    await send(`/api/users/${encodeURIComponent(slug)}/reports`, 'POST', { reason });
}

// 프로젝트 채팅 API(projectChatApi)가 같은 응답 검증과 요청 처리를 쓴다.
export {
    RESPONSE_ERROR, identifier as validIdentifier, nullableText, record as responseRecord, nullableIdentifier,
    unreadCount as parseCount, timestamp as parseTimestamp, request as messagingRequest, send as messagingSend,
    withQuery as messagingQuery,
};
