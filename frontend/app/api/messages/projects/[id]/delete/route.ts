import { validChatId } from '@/lib/messaging/directChatApi';
import { messagingError, messagingWriteGuard, proxyMessaging } from '@/lib/messaging/messagingProxy';

// 프로젝트 채팅방 논리 삭제(D10). 요청 본문은 쓰지 않지만 쓰기 요청 검사를 위해 JSON으로 받는다.
export async function POST(request: Request, context: { params: Promise<{ id: string }> }) {
    const blocked = messagingWriteGuard(request);
    if (blocked) return blocked;
    const { id } = await context.params;
    if (!validChatId(id)) return messagingError(404, '프로젝트 채팅을 찾을 수 없어요.');
    return proxyMessaging(`/api/v1/messages/projects/${id}/delete`, { method: 'POST' }, undefined, {
        403: '주최자만 프로젝트 채팅방을 삭제할 수 있어요.',
        404: '프로젝트 채팅을 찾을 수 없어요.',
        409: '프로젝트가 종료된 뒤에 채팅방을 삭제할 수 있어요.',
    });
}
