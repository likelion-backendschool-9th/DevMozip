import type { ProfileIntroductionDetail } from '@/types/profileIntroduction';
import { ProfileSaveError } from './profileUpdate';

export function parseIntroduction(value: unknown): ProfileIntroductionDetail {
    if (!value || typeof value !== 'object') throw new Error('잘못된 자기소개 응답');
    const body = value as Record<string, unknown>;
    if (!(body.introduction === null || (typeof body.introduction === 'string' && body.introduction.length <= 20000))
        || (body.format !== 'text' && body.format !== 'html')) throw new Error('잘못된 자기소개 응답');
    return { introduction: body.introduction as string | null, format: body.format };
}
async function responseBody(response: Response) {
    if (!response.ok) {
        const error = await response.json().catch(() => null);
        throw new ProfileSaveError(response.status, typeof error?.message === 'string' ? error.message : '자기소개 요청을 완료하지 못했어요.');
    }
    return parseIntroduction(await response.json());
}
export async function fetchIntroduction(slug: string, own: boolean, signal?: AbortSignal) {
    return responseBody(await fetch(`/api/profiles/${own ? 'me' : encodeURIComponent(slug)}/introduction`, { cache: 'no-store', signal }));
}
export async function saveIntroduction(introduction: string, format: ProfileIntroductionDetail['format']) {
    return responseBody(await fetch('/api/profiles/me/introduction', { method: 'PUT', cache: 'no-store',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ introduction, format }) }));
}
