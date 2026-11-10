// 관리자 API 응답 타입 (docs/admin-plan.md 5절). 백엔드 /api/v1/admin/** 응답에서 화면이 쓰는 필드만 둔다.

export interface AdminPage<T> {
    items: T[];
    totalElements: number;
    page: number;
    totalPages: number;
}

// 14.2·14.3 회원 조회·제재
export type AdminMemberRole = 'USER' | 'PREMIUM' | 'OWNER' | 'ADMIN';
export type AdminMemberStatus = 'ACTIVE' | 'WARNED' | 'SUSPENDED';
/** 제재 하나 (ERD sanctions). revokedAt이 있으면 해제(철회)된 제재다. endsAt은 기한 있는 제재가 없어 늘 null이다. */
export interface AdminSanction {
    type: 'WARNED' | 'SUSPENDED';
    reason: string | null;
    adminNickname: string;
    startsAt: string;
    endsAt: string | null;
    revokedAt: string | null;
}
export interface AdminMember {
    id: number;
    /** 태그가 없는 회원은 null이다. 이때는 프로필 링크와 제재 버튼을 두지 않는다. */
    tag: string | null;
    nickname: string;
    email: string | null;
    role: AdminMemberRole | null;
    status: AdminMemberStatus | null;
    latestSanction: AdminSanction | null;
}

// 14.4·14.5 프로젝트 공고 관리
export interface AdminProject {
    id: number;
    title: string;
    hostTag: string | null;
    hostNickname: string | null;
    status: 'RECRUITING' | 'RECRUITMENT_COMPLETED' | 'IN_PROGRESS' | 'COMPLETED';
    /** 주최자가 정한 공개 여부 */
    isPublic: boolean;
    /** 관리자 숨김 */
    restricted: boolean;
    recruitmentEnd: string;
    developmentEnd: string;
    createdAt: string;
}

// 14.8 신고 내역 처리
export type AdminReportType = 'PROJECT' | 'MEMBER' | 'USER' | 'POST' | 'COMMENT';
export type AdminReportStatus = 'AWATING' | 'REVIEWING' | 'RESOLVED' | 'REJECTED';
/** 회원 표시. 탈퇴 회원은 tag가 null이고 withdrawn이 true다. */
export interface AdminParty { id: number; tag: string | null; nickname: string; withdrawn: boolean }
export interface AdminReport {
    id: number;
    type: AdminReportType;
    reporter: AdminParty;
    target: {
        projectId: number | null;
        projectTitle: string | null;
        /** 신고 대상 회원, 또는 대상 글·댓글의 작성자 */
        user: AdminParty | null;
        postId: number | null;
        commentId: number | null;
        content: string | null;
        deleted: boolean;
    };
    reason: string;
    status: AdminReportStatus;
    resolution: string | null;
    handledByNickname: string | null;
    handledAt: string | null;
    createdAt: string;
}

// 14.1 관리자 대시보드
export interface AdminRecentAction { type: 'SANCTION' | 'REPORT'; actorNickname: string | null; summary: string; createdAt: string }
export interface AdminDashboard {
    members: number;
    premiumMembers: number;
    suspendedMembers: number;
    projects: number;
    posts: number;
    pendingReports: number;
    aiRecommendationsThisMonth: number;
    recentActions: AdminRecentAction[];
}

/** 요약 값이 있는 관리자 목록 (AI 사용량·구독·결제) */
export interface AdminSummaryPage<S, T> extends AdminPage<T> {
    summary: S;
}

// 14.10·14.11 AI 사용량·오류
export interface AdminAiUsageSummary { month: string; totalUsed: number; totalPending: number; members: number }
export interface AdminAiUsage {
    userId: number;
    tag: string | null;
    nickname: string | null;
    role: string | null;
    withdrawn: boolean;
    usedCount: number;
    pendingCount: number;
}
export interface AdminAiFailure {
    requestId: number;
    conversationId: number;
    userId: number | null;
    userTag: string | null;
    nickname: string | null;
    withdrawn: boolean;
    status: string;
    createdAt: string;
    completedAt: string | null;
}

// 14.12·14.13 구독·결제 관리
export type AdminSubscriptionStatus = 'ACTIVE' | 'RENEWING' | 'CANCEL_REQUESTED' | 'EXPIRED' | 'CANCELED';
export type AdminPaymentStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELED';
export interface AdminSubscription {
    id: number;
    member: AdminParty;
    planName: string;
    status: AdminSubscriptionStatus;
    startsAt: string;
    currentPeriodEnd: string;
    cancelRequestedAt: string | null;
    endedAt: string | null;
}
export interface AdminSubscriptionSummary { statusCounts: Record<AdminSubscriptionStatus, number>; current: number }
export interface AdminPayment {
    id: number;
    orderId: string;
    member: AdminParty;
    amount: number;
    currency: string;
    status: AdminPaymentStatus;
    provider: string;
    /** 결제 당시 표시용 결제 수단 */
    method: string | null;
    paidAt: string | null;
    createdAt: string;
}
export interface AdminPaymentSummary { paidTotals: { currency: string; amount: number }[] }

// 14.9 리뷰 키워드 관리
export interface AdminReviewKeyword { id: number; name: string; active: boolean }

// 14.6·14.7 게시글·댓글 관리
export type AdminPostVisibility = 'VISIBLE' | 'HIDDEN' | 'RESTRICTED';
export interface AdminPost {
    id: number;
    type: 'POST' | 'REPOST';
    /** 퍼간 글은 제목이 없다. */
    title: string | null;
    content: string | null;
    authorTag: string | null;
    authorNickname: string;
    visibility: AdminPostVisibility;
    createdAt: string;
}
export interface AdminComment {
    id: number;
    postId: number;
    postTitle: string | null;
    content: string;
    authorTag: string | null;
    authorNickname: string;
    createdAt: string;
}
