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
