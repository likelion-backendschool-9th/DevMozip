// 팔로우 API(WBS 9.1~9.5) 응답. Mock 화면이 쓰는 types/follow.ts와 따로 둔다.

// GET /api/v1/users/{slug}/follow: 수는 대상 기준, followed·activityNotificationEnabled는 요청자 기준
export interface FollowStatus {
    followerCount: number;
    followingCount: number;
    followed: boolean;
    activityNotificationEnabled: boolean;
}

// POST·DELETE /api/v1/users/{slug}/follow: 처리 후의 최종 상태
export interface FollowResult {
    followed: boolean;
    activityNotificationEnabled: boolean;
    followerCount: number;
}

export type FollowListTab = 'followers' | 'following';

// 목록의 한 사람. 태그가 없는 회원은 tag가 null이다. 목록에는 회원 상태 조건이 없어 닉네임이 비어 있을 수도 있다.
export interface FollowUser {
    tag: string | null;
    nickname: string | null;
    profileImage: string | null;
    jobCategories: string[];
    career: string | null;
    skills: string[];
    collaborationStatus: string | null;
    followed: boolean;
    activityNotificationEnabled: boolean;
}

export interface FollowPage {
    items: FollowUser[];
    page: number;
    size: number;
    hasNext: boolean;
    totalCount: number;
}

export interface FollowListQuery {
    q: string;
    available: boolean;
    page: number;
    size: number;
}

export interface FollowNotification {
    activityNotificationEnabled: boolean;
}
