import type { ProfileDetail } from '@/types/profileDetail';

export const validProfileSlug = (slug: string) => /^[A-Za-z0-9_-]{1,100}$/.test(slug);
const nullableText = (value: unknown) => value === null || typeof value === 'string';
const identifier = (value: unknown) => typeof value === 'number' && Number.isSafeInteger(value) && value > 0;

export function parseProfileDetail(value: unknown): ProfileDetail {
    if (!value || typeof value !== 'object') throw new Error('잘못된 프로필 응답');
    const p = value as Record<string, unknown>;
    const job = p.jobCategory as Record<string, unknown> | null;
    if (!identifier(p.id) || typeof p.tag !== 'string' || !validProfileSlug(p.tag)
        || typeof p.nickname !== 'string' || !p.nickname.trim()
        || !job || !identifier(job.id) || !nullableText(job.name)
        || !nullableText(p.summary) || !nullableText(p.profileImage) || !nullableText(p.bannerImage)
        || !nullableText(p.collaborationStatus)
        || !(p.career === null || (typeof p.career === 'number' && Number.isSafeInteger(p.career) && p.career >= 0))
        || typeof p.trustScore !== 'number' || !Number.isFinite(p.trustScore)) {
        throw new Error('잘못된 프로필 응답');
    }
    // 알 수 없는 추가 필드(이메일 등)는 브라우저 응답에 전달하지 않는다.
    return { id: p.id as number, tag: p.tag, nickname: p.nickname,
        jobCategory: { id: job.id as number, name: job.name as string | null },
        summary: p.summary as string | null, career: p.career as number | null,
        profileImage: p.profileImage as string | null, bannerImage: p.bannerImage as string | null,
        collaborationStatus: p.collaborationStatus as string | null, trustScore: p.trustScore };
}

export function formatCareer(months: number | null): string {
    if (months === null) return '경력 미입력';
    if (months === 0) return '신입';
    const years = Math.floor(months / 12);
    const rest = months % 12;
    return [years ? `${years}년` : '', rest ? `${rest}개월` : ''].filter(Boolean).join(' ');
}

export function collaborationLabel(status: string | null): string {
    if (status === 'AVAILABLE') return '협업 가능';
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
