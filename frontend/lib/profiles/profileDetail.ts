import type { ProfileDetail, OwnProfileDetail } from '@/types/profileDetail';

export const validProfileSlug = (slug: string) => /^[A-Za-z0-9_-]{1,100}$/.test(slug);
const nullableText = (value: unknown) => value === null || typeof value === 'string';
const identifier = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;

function readProfileDetail(value: unknown, allowUnsetTag: boolean): OwnProfileDetail {
    if (!value || typeof value !== 'object') throw new Error('잘못된 프로필 응답');
    const p = value as Record<string, unknown>;
    if (!identifier(p.id) || !(typeof p.tag === 'string' && validProfileSlug(p.tag) || allowUnsetTag && p.tag === null)
        || typeof p.nickname !== 'string' || !p.nickname.trim()
        || !Array.isArray(p.jobCategories) || !p.jobCategories.every(job => typeof job === 'string')
        || !nullableText(p.summary) || !nullableText(p.profileImage) || !nullableText(p.bannerImage)
        || !nullableText(p.collaborationStatus)
        || !nullableText(p.career)
        || !(p.trustScore === null || (typeof p.trustScore === 'number' && Number.isFinite(p.trustScore)))
        || !(p.contactHours === undefined || nullableText(p.contactHours))) {
        throw new Error('잘못된 프로필 응답');
    }
    // 알 수 없는 추가 필드(이메일 등)는 브라우저 응답에 전달하지 않는다.
    return { id: p.id as number, tag: p.tag as string | null, nickname: p.nickname,
        jobCategories: p.jobCategories as string[],
        summary: p.summary as string | null, career: p.career as string | null,
        profileImage: p.profileImage as string | null, bannerImage: p.bannerImage as string | null,
        collaborationStatus: p.collaborationStatus as string | null, trustScore: p.trustScore as number | null,
        ...(p.contactHours !== undefined ? { contactHours: p.contactHours as string | null } : {}) };
}

export function parseProfileDetail(value: unknown): ProfileDetail {
    return readProfileDetail(value, false) as ProfileDetail;
}
export function parseOwnProfileDetail(value: unknown): OwnProfileDetail {
    return readProfileDetail(value, true);
}

export function formatCareer(career: string | null): string {
    return career || '경력 미입력';
}

export function collaborationLabel(status: string | null): string {
    if (status === 'AVAILABLE') return '협업 가능';
    if (status === 'BUSY') return '현재 협업 어려움';
    if (status === 'OPEN_TO_OFFERS') return '제안 검토 가능';
    if (status === 'RESTING') return '쉬는 중';
    if (status === 'UNAVAILABLE') return '현재 협업 쉬는 중';
    return '협업 상태 미등록';
}

export function profileImageUrl(value: string | null): string | undefined {
    if (!value) return undefined;
    // 브라우저가 공개 접근할 수 있는 http(s) URL 또는 같은 출처 경로만 허용한다.
    if (value.startsWith('/') && !value.startsWith('//') && !value.includes('\\')) return value;
    try {
        const url = new URL(value);
        return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password ? url.href : undefined;
    } catch { return undefined; }
}

export class ProfileRequestError extends Error {
    constructor(public readonly status: number) { super('프로필 조회 실패'); }
}

export async function fetchProfileDetail(slug: string, signal: AbortSignal): Promise<ProfileDetail> {
    const response = await fetch(`/api/profiles/${encodeURIComponent(slug)}`, { cache: 'no-store', signal });
    if (!response.ok) throw new ProfileRequestError(response.status);
    const profile = parseProfileDetail(await response.json());
    if (profile.tag !== slug) throw new Error('프로필 태그 불일치');
    return profile;
}
