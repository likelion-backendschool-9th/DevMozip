import { directMessageBody, parseDirectChatDetail, parseDirectChatMessage, validChatId } from '@/lib/messaging/directChatApi';
import { forwardedQuery, jsonRequest, messagingError, messagingWriteGuard, proxyMessaging, readJsonObject } from '@/lib/messaging/messagingProxy';

type Context = { params: Promise<{ id: string }> };
const NOT_FOUND = '대화를 찾을 수 없어요.';

export async function GET(request: Request, context: Context) {
    const { id } = await context.params;
    if (!validChatId(id)) return messagingError(404, NOT_FOUND);
    return proxyMessaging(`/api/v1/messages/chats/${id}${forwardedQuery(request, ['before', 'after', 'size'])}`, {}, parseDirectChatDetail,
        { 400: '조회 조건을 확인해 주세요.' });
}

export async function POST(request: Request, context: Context) {
    const blocked = messagingWriteGuard(request);
    if (blocked) return blocked;
    const { id } = await context.params;
    if (!validChatId(id)) return messagingError(404, NOT_FOUND);
    const content = directMessageBody((await readJsonObject(request))?.body);
    if (content === null) return messagingError(400, '메시지는 1자 이상 2,000자 이하로 입력해 주세요.');
    return proxyMessaging(`/api/v1/messages/chats/${id}`, jsonRequest('POST', { body: content }), parseDirectChatMessage, {
        400: '메시지에 사용할 수 없는 문자가 있어요.',
        409: '상대가 이용 정지 상태라 메시지를 보낼 수 없어요.',
    });
}
