import { notificationListQuery, notificationListSearch, parseNotificationPage } from '@/lib/notifications/notificationApi';
import { notificationError, proxyNotification } from '@/lib/notifications/notificationProxy';

// 알림 목록(12.1·12.4·12.5). status·page·size만 확인해 백엔드로 보낸다. 요청자는 백엔드가 토큰으로 정한다.
export async function GET(request: Request) {
    const query = notificationListQuery(new URL(request.url).searchParams);
    if (!query) return notificationError(400, '조회 조건을 확인해 주세요.');
    return proxyNotification(`/api/v1/notifications?${notificationListSearch(query)}`, {}, parseNotificationPage,
        page => page.page === query.page && page.size === query.size);
}
