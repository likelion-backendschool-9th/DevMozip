import type { ProfileLinksDetail } from '@/types/profileLinks';
import { ProfileSaveError } from './profileUpdate';

export function profileLinkUrl(value: unknown, optional = false): string {
    if (typeof value !== 'string' || value.length > 500) throw new Error('URL은 500자 이내로 입력해 주세요.');
    const trimmed = value.trim();
    if (!trimmed && optional) return '';
    if (!/^https?:\/\//i.test(trimmed) || /[\\\x00-\x20\x7f]/.test(trimmed)) throw new Error('http 또는 https 주소를 입력해 주세요.');
    try {
        const url = new URL(trimmed);
        if (!url.hostname || url.username || url.password) throw new Error();
    } catch { throw new Error('올바른 http 또는 https 주소를 입력해 주세요.'); }
    return trimmed;
}
export function parseProfileLinks(value: unknown): ProfileLinksDetail {
    if (!value || typeof value !== 'object') throw new Error('외부 링크 응답을 확인할 수 없어요.');
    const body = value as Record<string, unknown>;
    if (!Array.isArray(body.externalLinks) || body.externalLinks.length > 10) throw new Error('추가 외부 링크는 최대 10개까지 등록할 수 있어요.');
    return { github: profileLinkUrl(body.github, true), blog: profileLinkUrl(body.blog, true),
        externalLinks: body.externalLinks.map(item => {
            if (!item || typeof item.label !== 'string' || !item.label.trim() || item.label.length > 40) throw new Error('링크 이름은 1~40자로 입력해 주세요.');
            return { label: item.label.trim(), url: profileLinkUrl(item.url) };
        }) };
}
async function responseBody(response: Response) {
    if (!response.ok) {
        const error = await response.json().catch(() => null);
        throw new ProfileSaveError(response.status, typeof error?.message === 'string' ? error.message : '외부 링크 요청을 완료하지 못했어요.');
    }
    return parseProfileLinks(await response.json());
}
export async function fetchProfileLinks(slug: string, own: boolean, signal?: AbortSignal) {
    return responseBody(await fetch(`/api/profiles/${own ? 'me' : encodeURIComponent(slug)}/links`, { cache: 'no-store', signal }));
}
export async function saveProfileLinks(value: ProfileLinksDetail) {
    return responseBody(await fetch('/api/profiles/me/links', { method: 'PUT', cache: 'no-store',
        headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(parseProfileLinks(value)) }));
}
