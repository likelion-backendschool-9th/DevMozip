import { notificationId, parseNotificationReadResult } from '@/lib/notifications/notificationApi';
import { notificationError, notificationRequestGuard, proxyNotification, readNotificationBody } from '@/lib/notifications/notificationProxy';

type Context = { params: Promise<{ id: string }> };

// 읽음·안 읽음 전환(12.2). 본문이 없으면 읽음이다. 리뷰 알림의 읽음은 리뷰 작성 상태를 바꾸지 않는다.
export async function PATCH(request: Request, context: Context) {
    const blocked = notificationRequestGuard(request);
    if (blocked) return blocked;
    const id = notificationId((await context.params).id);
    if (id === null) return notificationError(400, '알림 ID를 확인해 주세요.');
    const read = await readNotificationBody(request);
    if (read === null) return notificationError(400, '읽음 여부를 확인해 주세요.');
    return proxyNotification(`/api/v1/notifications/${id}/read`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ read }),
    }, parseNotificationReadResult, result => result.id === id);
}
