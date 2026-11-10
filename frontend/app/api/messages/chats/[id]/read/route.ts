import { validChatId } from '@/lib/messaging/directChatApi';
import { jsonRequest, messagingError, messagingWriteGuard, proxyMessaging, readJsonObject } from '@/lib/messaging/messagingProxy';

const READ_ERROR = '읽은 메시지를 확인해 주세요.';

export async function PATCH(request: Request, context: { params: Promise<{ id: string }> }) {
    const blocked = messagingWriteGuard(request);
    if (blocked) return blocked;
    const { id } = await context.params;
    if (!validChatId(id)) return messagingError(404, '대화를 찾을 수 없어요.');
    const lastReadMessageId = (await readJsonObject(request))?.lastReadMessageId;
    if (typeof lastReadMessageId !== 'number' || !Number.isSafeInteger(lastReadMessageId) || lastReadMessageId <= 0) return messagingError(400, READ_ERROR);
    return proxyMessaging(`/api/v1/messages/chats/${id}/read`, jsonRequest('PATCH', { lastReadMessageId }), undefined, { 400: READ_ERROR });
}
