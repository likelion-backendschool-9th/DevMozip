// SNS 피드 API(WBS 8.1~8.16) 응답. Mock 화면이 쓰는 types/social.ts와 따로 둔다 (docs/feed-api.md).

export type FeedCategoryCode = 'INFO' | 'FEEDBACK' | 'PROMO' | 'COMMUNITY';
export type FeedPostType = 'POST' | 'REPOST';
export type FeedVisibility = 'VISIBLE' | 'HIDDEN' | 'RESTRICTED';
export type FeedListMode = 'all' | 'recommended' | 'following';
export type FeedSortValue = 'recommended' | 'popular' | 'views' | 'latest';

// 작성자. tag가 없으면 프로필 링크를 두지 않는다.
export interface FeedAuthor {
    id: number;
    nickname: string | null;
    tag: string | null;
    profileImage: string | null;
    jobCategories: string[];
}

// 퍼간 글의 원본 요약. available이 false면 원본이 삭제·숨김이라 id 외의 값이 비어 있다.
export interface FeedOriginal {
    id: number;
    available: boolean;
    category: FeedCategoryCode | null;
    title: string | null;
    author: FeedAuthor | null;
}

// 요청자가 이 원본을 퍼간 글
export interface FeedMyRepost {
    id: number;
    content: string | null;
}

export interface FeedPost {
    id: number;
    type: FeedPostType;
    category: FeedCategoryCode;
    title: string | null;
    content: string | null;
    techStacks: string[];
    interests: string[];
    imageUrls: string[];
    viewCount: number;
    likeCount: number;
    commentCount: number;
    visibility: FeedVisibility;
    author: FeedAuthor;
    original: FeedOriginal | null;
    createdAt: string;
    updatedAt: string;
    edited: boolean;
    mine: boolean;
    liked: boolean;
    bookmarked: boolean;
    reported: boolean;
    myRepost: FeedMyRepost | null;
}

export interface FeedPage {
    items: FeedPost[];
    page: number;
    size: number;
    hasNext: boolean;
    totalCount: number;
}

// 목록 조건. category가 null이면 전체다. seed는 추천순에서 다음 페이지에도 같은 값을 보낸다.
export interface FeedListQuery {
    category: FeedCategoryCode | null;
    q: string;
    sort: FeedSortValue;
    page: number;
    size: number;
    seed: number;
}

export interface FeedComment {
    id: number;
    postId: number;
    content: string;
    author: FeedAuthor;
    createdAt: string;
    updatedAt: string;
    edited: boolean;
    mine: boolean;
    reported: boolean;
}

export interface FeedLikeResult {
    postId: number;
    liked: boolean;
    likeCount: number;
}

export interface FeedBookmarkResult {
    postId: number;
    bookmarked: boolean;
}

export interface FeedReportResult {
    id: number;
    targetType: 'POST' | 'COMMENT';
    targetId: number;
    status: string;
    createdAt: string;
}

// 작성·수정 화면의 입력. 새 이미지는 File, 남길 기존 이미지는 서버 경로(/images/...)다.
export interface FeedPostDraft {
    category: FeedCategoryCode;
    title: string;
    content: string;
    techStacks: string[];
    interests: string[];
    keepImageUrls: string[];
    files: File[];
}
