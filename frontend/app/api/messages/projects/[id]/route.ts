import { directMessageBody, validChatId } from '@/lib/messaging/directChatApi';
import { forwardedQuery, jsonRequest, messagingError, messagingWriteGuard, proxyMessaging, readJsonObject } from '@/lib/messaging/messagingProxy';
import { parseProjectChatDetail, parseProjectChatMessage } from '@/lib/messaging/projectChatApi';

// {id}는 프로젝트 ID다. 입장할 수 없는 경우(멤버가 아님, 모집 시작 전, 삭제된 방·프로젝트)는 모두 404다.
type Context = { params: Promise<{ id: string }> };
const NOT_FOUND = '프로젝트 채팅에 입장할 수 없어요.';

export async function GET(request: Request, context: Context) {
    const { id } = await context.params;
    if (!validChatId(id)) return messagingError(404, NOT_FOUND);
    return proxyMessaging(`/api/v1/messages/projects/${id}${forwardedQuery(request, ['before', 'after', 'size'])}`, {}, parseProjectChatDetail,
        { 400: '조회 조건을 확인해 주세요.', 404: NOT_FOUND });
}

export async function POST(request: Request, context: Context) {
    const blocked = messagingWriteGuard(request);
    if (blocked) return blocked;
    const { id } = await context.params;
    if (!validChatId(id)) return messagingError(404, NOT_FOUND);
    const content = directMessageBody((await readJsonObject(request))?.body);
    if (content === null) return messagingError(400, '메시지는 1자 이상 2,000자 이하로 입력해 주세요.');
    return proxyMessaging(`/api/v1/messages/projects/${id}`, jsonRequest('POST', { body: content }), parseProjectChatMessage, {
        400: '메시지에 사용할 수 없는 문자가 있어요.',
        404: NOT_FOUND,
        409: '종료된 프로젝트 채팅에는 메시지를 보낼 수 없어요.',
    });
}
