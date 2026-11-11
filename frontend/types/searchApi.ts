// 통합 검색 API(GET /api/v1/search, WBS 6.1~6.10) 응답. Mock 화면이 쓰는 types/search.ts와 따로 둔다.

export type SearchApiType = 'developers' | 'projects';

// 프로젝트 탐색의 모집 상태 5개. RECRUITMENT_CLOSED는 검색 API에서만 쓰는 값이다(모집 중이지만 마감일이 지남).
export type ProjectSearchStatus = 'RECRUITING' | 'RECRUITMENT_CLOSED' | 'RECRUITMENT_COMPLETED' | 'IN_PROGRESS' | 'COMPLETED';

// 개발자 카드 한 장. followed는 요청자가 이 회원을 팔로우하는지다(비로그인이면 false).
export interface DeveloperSearchItem {
    tag: string;
    nickname: string | null;
    profileImage: string | null;
    jobCategories: string[];
    career: string | null;
    summary: string | null;
    skills: string[];
    interests: string[];
    collaborationStatus: string | null;
    trustScore: number | null;
    followed: boolean;
}

// 프로젝트 카드 한 장. jobRoles는 모집 직군 코드(기본 직군) 또는 직접 입력한 직군이다.
export interface ProjectSearchItem {
    id: number;
    title: string;
    summary: string | null;
    imageUrl: string | null;
    status: ProjectSearchStatus;
    jobRoles: string[];
    techStacks: string[];
    interests: string[];
    totalCapacity: number;
    memberCount: number;
    developmentStart: string;
    developmentEnd: string;
    createdAt: string;
}

// totalCount는 검색 조건을 적용한 전체 수다. 전체 페이지보다 큰 page는 빈 items로 온다.
export interface SearchPage<T> {
    items: T[];
    page: number;
    size: number;
    totalCount: number;
    totalPages: number;
}

export type SearchResult =
    | { type: 'developers'; page: SearchPage<DeveloperSearchItem> }
    | { type: 'projects'; page: SearchPage<ProjectSearchItem> };

// 필터 선택지. Mock 켜기는 Mock 저장소에서, Mock 끄기는 백엔드 enum(가입 선택지)에서 만든다.
export interface SearchFilterChoices {
    jobs: string[];
    careers: string[];
    roles: string[];
    statuses: string[];
    fields: string[];
    stacks: string[];
}
