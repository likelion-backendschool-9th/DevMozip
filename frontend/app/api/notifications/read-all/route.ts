import { parseNotificationReadAll } from '@/lib/notifications/notificationApi';
import { notificationRequestGuard, proxyNotification } from '@/lib/notifications/notificationProxy';

// 모두 읽음. WBS에 없는 API다(docs/notification-plan.md D5).
export async function PATCH(request: Request) {
    const blocked = notificationRequestGuard(request);
    if (blocked) return blocked;
    return proxyNotification('/api/v1/notifications/read-all', { method: 'PATCH' }, parseNotificationReadAll);
}
