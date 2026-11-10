import { validProfileSlug } from '@/lib/profiles/profileDetail';
import {
    DirectChatRequestError, RESPONSE_ERROR, directMessageBody, messagingQuery, messagingRequest, messagingSend, nullableIdentifier,
    nullableText, parseCount, parseTimestamp, responseRecord, validIdentifier,
} from '@/lib/messaging/directChatApi';
import type {
    ProjectChatDetail, ProjectChatList, ProjectChatMember, ProjectChatMessage, ProjectChatStatus, ProjectChatSummary,
} from '@/types/messaging';

// 프로젝트 단톡방(7.18~7.23)의 BFF 호출과 응답 검증. 오류는 1:1과 같은 DirectChatRequestError를 쓴다.
const PROJECTS = '/api/messages/projects';
const STATUSES: ProjectChatStatus[] = ['RECRUITING', 'RECRUITMENT_COMPLETED', 'IN_PROGRESS', 'COMPLETED'];

const status = (value: unknown): ProjectChatStatus => {
    if (!STATUSES.includes(value as ProjectChatStatus)) throw new Error(RESPONSE_ERROR);
    return value as ProjectChatStatus;
};
const optionalTag = (value: unknown) => {
    if (!nullableText(value) || (value !== null && !validProfileSlug(value))) throw new Error(RESPONSE_ERROR);
    return value;
};
const text = (value: unknown) => {
    if (typeof value !== 'string') throw new Error(RESPONSE_ERROR);
    return value;
};
const optionalText = (value: unknown) => {
    if (!nullableText(value)) throw new Error(RESPONSE_ERROR);
    return value;
};

function parseSummary(value: unknown): ProjectChatSummary {
    const item = responseRecord(value);
    if (!validIdentifier(item.id)) throw new Error(RESPONSE_ERROR);
    return { id: item.id, title: text(item.title), status: status(item.status), memberCount: parseCount(item.memberCount), unreadCount: parseCount(item.unreadCount) };
}

export function parseProjectChatList(value: unknown): ProjectChatList {
    const list = responseRecord(value);
    if (!Array.isArray(list.items)) throw new Error(RESPONSE_ERROR);
    return { items: list.items.map(parseSummary), nextCursor: nullableIdentifier(list.nextCursor) };
}

function parseMember(value: unknown): ProjectChatMember {
    const member = responseRecord(value);
    if (!validIdentifier(member.id) || typeof member.me !== 'boolean') throw new Error(RESPONSE_ERROR);
    if (member.role !== null && member.role !== 'HOST' && member.role !== 'PARTICIPANT') throw new Error(RESPONSE_ERROR);
    return {
        id: member.id, me: member.me, tag: optionalTag(member.tag), nickname: optionalText(member.nickname),
        profileImage: optionalText(member.profileImage), role: member.role as ProjectChatMember['role'],
        lastReadMessageId: nullableIdentifier(member.lastReadMessageId), joinedAt: parseTimestamp(member.joinedAt),
        leftAt: member.leftAt === null ? null : parseTimestamp(member.leftAt),
    };
}

export function parseProjectChatMessage(value: unknown): ProjectChatMessage {
    const message = responseRecord(value);
    const sender = responseRecord(message.sender);
    if (!validIdentifier(message.id) || typeof message.body !== 'string' || typeof message.mine !== 'boolean' || !validIdentifier(sender.memberId)) throw new Error(RESPONSE_ERROR);
    return {
        id: message.id, body: message.body, mine: message.mine,
        sender: { memberId: sender.memberId, tag: optionalTag(sender.tag), nickname: optionalText(sender.nickname) },
        createdAt: parseTimestamp(message.createdAt),
    };
}

export function parseProjectChatDetail(value: unknown): ProjectChatDetail {
    const detail = responseRecord(value);
    if (!validIdentifier(detail.projectId) || typeof detail.canSend !== 'boolean' || typeof detail.isHost !== 'boolean'
        || !Array.isArray(detail.members) || !Array.isArray(detail.messages)) throw new Error(RESPONSE_ERROR);
    return {
        projectId: detail.projectId, title: text(detail.title), status: status(detail.status), canSend: detail.canSend, isHost: detail.isHost,
        members: detail.members.map(parseMember), messages: detail.messages.map(parseProjectChatMessage), nextBefore: nullableIdentifier(detail.nextBefore),
    };
}

export async function fetchProjectChats(query: { q?: string; cursor?: number; size?: number } = {}, signal?: AbortSignal) {
    return parseProjectChatList(await (await messagingRequest(messagingQuery('/api/messages/chats', { type: 'project', ...query }), { signal })).json());
}

export async function fetchProjectChat(projectId: number, query: { before?: number; after?: number; size?: number } = {}, signal?: AbortSignal) {
    return parseProjectChatDetail(await (await messagingRequest(messagingQuery(`${PROJECTS}/${projectId}`, query), { signal })).json());
}

export async function sendProjectMessage(projectId: number, body: string) {
    const content = directMessageBody(body);
    if (content === null) throw new DirectChatRequestError(400, '메시지는 1자 이상 2,000자 이하로 입력해 주세요.');
    return parseProjectChatMessage(await (await messagingSend(`${PROJECTS}/${projectId}`, 'POST', { body: content })).json());
}

export async function markProjectChatRead(projectId: number, lastReadMessageId: number) {
    await messagingSend(`${PROJECTS}/${projectId}/read`, 'PATCH', { lastReadMessageId });
}

export async function fetchProjectUnreadCount(signal?: AbortSignal) {
    return { count: parseCount(responseRecord(await (await messagingRequest(`${PROJECTS}/unread-count`, { signal })).json()).count) };
}

/** 종료된 프로젝트의 주최자가 채팅방을 논리 삭제한다. 프로젝트 관리 화면(PR #81·#85)이 종료 창에서 부른다. */
export async function deleteProjectChat(projectId: number) {
    await messagingSend(`${PROJECTS}/${projectId}/delete`, 'POST', {});
}

/** 화면 표시용 프로젝트 상태. 기존 Mock 화면의 문구와 같다. */
export const projectChatStatusLabel: Record<ProjectChatStatus, string> = {
    RECRUITING: '모집 중', RECRUITMENT_COMPLETED: '모집 완료', IN_PROGRESS: '프로젝트 진행 중', COMPLETED: '프로젝트 종료',
};
