import type { ProfileReviewItem, ProfileReviewsPage } from '@/types/profileReviews';
import { validProfileSlug } from './profileDetail';
const integer = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
const positive = (value: unknown): value is number => integer(value) && value > 0;
const date = (value: unknown): value is string | null => value === null || typeof value === 'string' && /(?:Z|[+-]\d{2}:\d{2})$/.test(value) && Number.isFinite(Date.parse(value));
export function parseProfileReviews(value: unknown): ProfileReviewsPage {
    if (!value || typeof value !== 'object') throw new Error('리뷰 응답 오류');
    const p = value as Record<string, unknown>;
    if (!positive(p.userId) || !integer(p.count) || typeof p.average !== 'number' || !Number.isFinite(p.average)
        || (p.count === 0 ? p.average !== 0 : p.average < 1 || p.average > 5)
        || !integer(p.page) || p.page > 1000000 || !positive(p.size) || p.size > 100 || !integer(p.totalPages)
        || p.totalPages !== Math.ceil(p.count / p.size) || !Array.isArray(p.items) || p.items.length > p.size || p.items.length > p.count
        || !Array.isArray(p.distribution) || p.distribution.length !== 5 || !p.distribution.every(integer)
        || p.distribution.reduce((a, b) => a + b, 0) !== p.count || !Array.isArray(p.keywords)) throw new Error('리뷰 집계 오류');
    const keywords = p.keywords.map(k => {
        if (!k || typeof k.name !== 'string' || !k.name || !positive(k.count) || k.count > (p.count as number)) throw new Error('키워드 집계 오류');
        return { name: k.name, count: k.count };
    });
    if (new Set(keywords.map(k => k.name)).size !== keywords.length) throw new Error('중복 키워드');
    const items: ProfileReviewItem[] = p.items.map(r => {
        if (!r || !positive(r.id) || !(r.projectId === null || positive(r.projectId)) || typeof r.projectTitle !== 'string'
            || !positive(r.authorUserId) || typeof r.authorNickname !== 'string'
            || !(r.authorTag === null || typeof r.authorTag === 'string' && validProfileSlug(r.authorTag))
            || !integer(r.rating) || r.rating < 1 || r.rating > 5 || !Array.isArray(r.keywords) || r.keywords.length > 5
            || !r.keywords.every((k: unknown) => typeof k === 'string' && k.length > 0)
            || new Set(r.keywords).size !== r.keywords.length || !date(r.submittedAt)) throw new Error('리뷰 항목 오류');
        return { id: r.id, projectId: r.projectId, projectTitle: r.projectTitle, authorUserId: r.authorUserId,
            authorNickname: r.authorNickname, authorTag: r.authorTag, rating: r.rating, keywords: [...r.keywords], submittedAt: r.submittedAt };
    });
    if (new Set(items.map(r => r.id)).size !== items.length) throw new Error('중복 리뷰');
    return { userId: p.userId, count: p.count, average: p.average, distribution: [...p.distribution], keywords, items, page: p.page, size: p.size, totalPages: p.totalPages };
}
export async function fetchProfileReviews(target: string, page: number, signal?: AbortSignal): Promise<ProfileReviewsPage> {
    const response = await fetch(`/api/profiles/${encodeURIComponent(target)}/reviews?page=${page}&size=20`, { cache: 'no-store', signal });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(typeof body?.message === 'string' ? body.message : '리뷰를 불러오지 못했어요.');
    }
    const value = parseProfileReviews(await response.json());
    if (value.page !== page || value.size !== 20) throw new Error('리뷰 페이지가 일치하지 않습니다.');
    return value;
}
