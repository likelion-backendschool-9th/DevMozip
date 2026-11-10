'use client';
import { MockNotificationList } from '@/components/notifications/MockNotificationList';
import { NotificationList } from '@/components/notifications/NotificationList';
import { useMockVisibility } from '@/lib/state/mockVisibilityStore';

// 알림 전체 페이지(WBS 12.1). Mock 토글 ON이면 기존 Mock 알림, OFF이면 서버 알림(/api/notifications)을 보여 준다.
// 헤더 종 버튼의 공통 알림 모달과 같은 목록 컴포넌트를 쓴다.
export default function NotificationsPage() {
    const { enabled } = useMockVisibility();
    return enabled ? <MockNotificationList /> : <NotificationList variant="page" />;
}
