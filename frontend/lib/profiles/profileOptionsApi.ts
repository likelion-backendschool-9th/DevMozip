import type { ProfileOptionsDetail } from '@/types/profileOptions';
import { ProfileSaveError } from './profileUpdate';

export function tagList(value: unknown): value is string[] {
    return Array.isArray(value) && value.length <= 30 && value.every(item => typeof item === 'string' && item.trim().length > 0 && item.length <= 50);
}
export function parseProfileOptions(value: unknown): ProfileOptionsDetail {
    if (!value || typeof value !== 'object') throw new Error('잘못된 프로필 선택 정보 응답');
    const body = value as Record<string, unknown>;
    if (!tagList(body.skills) || !tagList(body.interests)) throw new Error('잘못된 프로필 선택 정보 응답');
    return { skills: body.skills, interests: body.interests };
}
async function responseBody(response: Response) {
    if (!response.ok) {
        const error = await response.json().catch(() => null);
        throw new ProfileSaveError(response.status, typeof error?.message === 'string' ? error.message : '프로필 선택 정보를 처리하지 못했어요.');
    }
    return parseProfileOptions(await response.json());
}
export async function fetchProfileOptions(slug: string, own: boolean, signal?: AbortSignal) {
    return responseBody(await fetch(`/api/profiles/${own ? 'me' : encodeURIComponent(slug)}/options`, { cache: 'no-store', signal }));
}
export async function saveProfileOptions(value: ProfileOptionsDetail) {
    const body = parseProfileOptions(value);
    return responseBody(await fetch('/api/profiles/me/options', { method: 'PUT', cache: 'no-store',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }));
}
