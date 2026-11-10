import { parseDirectChatList, parseStartedChat } from '@/lib/messaging/directChatApi';
import { forwardedQuery, jsonRequest, messagingError, messagingWriteGuard, proxyMessaging, readJsonObject } from '@/lib/messaging/messagingProxy';
import { parseProjectChatList } from '@/lib/messaging/projectChatApi';
import { validProfileSlug } from '@/lib/profiles/profileDetail';

const CHATS = '/api/v1/messages/chats';

/** 사이드바 탭마다 type으로 나눠 부른다. 두 탭의 항목 형식이 달라 type에 맞는 형식으로만 전달한다. */
export async function GET(request: Request) {
    const parse = new URL(request.url).searchParams.get('type') === 'project' ? parseProjectChatList : parseDirectChatList;
    return proxyMessaging(`${CHATS}${forwardedQuery(request, ['type', 'q', 'cursor', 'size'])}`, {}, parse,
        { 400: '검색 조건을 확인해 주세요.' });
}

export async function POST(request: Request) {
    const blocked = messagingWriteGuard(request);
    if (blocked) return blocked;
    const input = await readJsonObject(request);
    if (typeof input?.targetSlug !== 'string' || !validProfileSlug(input.targetSlug)) return messagingError(400, '대화 상대를 확인해 주세요.');
    return proxyMessaging(CHATS, jsonRequest('POST', { targetSlug: input.targetSlug }), parseStartedChat,
        { 400: '본인과는 대화를 시작할 수 없어요.', 404: '대화 상대를 찾을 수 없어요.' });
}
