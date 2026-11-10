// 알림 API(WBS 12.1~12.5) 응답. Mock 알림(types/messaging.ts의 UnifiedNotice)과 따로 둔다.

export type NotificationTab = 'all' | 'unread' | 'read';

/** PENDING: 작성 대기, COMPLETED: 작성 완료, EXPIRED: 기간 만료 */
export type NotificationReviewStatus = 'PENDING' | 'COMPLETED' | 'EXPIRED';

export interface NotificationReview {
    status: NotificationReviewStatus;
    expiresAt: string;
}

// GET /api/v1/notifications의 항목. read는 읽은 시각이 있는지, resolved는 리뷰 작성 완료·기간 만료로 처리가 끝났는지다.
// actionUrl이 내부 경로 조건을 통과하지 못하면 null로 바꿔 이동하지 않는다.
export interface ApiNotification {
    id: number;
    type: string;
    title: string;
    body: string;
    actionUrl: string | null;
    projectId: number | null;
    read: boolean;
    resolved: boolean;
    review: NotificationReview | null;
    createdAt: string;
}

// unreadCount는 탭·페이지와 관계없는 전체 안 읽음 수이며 헤더 배지가 쓴다.
export interface NotificationPage {
    items: ApiNotification[];
    page: number;
    size: number;
    hasNext: boolean;
    unreadCount: number;
}

export interface NotificationListQuery {
    status: NotificationTab;
    page: number;
    size: number;
}

// PATCH /api/v1/notifications/{id}/read
export interface NotificationReadResult {
    id: number;
    read: boolean;
    unreadCount: number;
}

// PATCH /api/v1/notifications/read-all
export interface NotificationReadAllResult {
    unreadCount: number;
}
