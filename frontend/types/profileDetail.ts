// GET /api/v1/users/{slug}: 공개 프로필 상단 응답. career는 선택한 경력 문구.
export interface ProfileDetail {
    id: number;
    tag: string;
    nickname: string;
    jobCategories: string[];
    summary: string | null;
    career: string | null;
    profileImage: string | null;
    bannerImage: string | null;
    collaborationStatus: string | null;
    trustScore: number | null;
    contactHours?: string | null;
}

// 본인 조회는 최초 사용자 아이디를 설정하기 전 tag가 null일 수 있다.
export type OwnProfileDetail = Omit<ProfileDetail, 'tag'> & { tag: string | null };

export interface ProfileUpdateInput {
    nickname: string;
    tag: string;
    jobCategories: string[];
    summary: string;
    career: string | null;
    collaborationStatus: string | null;
    contactHours: string;
}
export type ProfileImageKind = 'profile' | 'banner';
export interface ProfileImageResult { profileImage: string | null; bannerImage: string | null }
