import type { LikedPostAuthor, LikedPostItem, LikedPostsPage } from '@/types/likedPosts';
import { validProfileSlug } from './profileDetail';
import { ProfileSaveError } from './profileUpdate';
const integer = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const text = (v: unknown): v is string | null => v === null || typeof v === 'string';
const preview = (v: unknown): v is string | null => text(v) && (v === null || v.length <= 600);
const date = (v: unknown): v is string | null => v === null || typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(v) && Number.isFinite(Date.parse(v));
function author(value: unknown): LikedPostAuthor {
    if (!value || typeof value !== 'object') throw new Error('작성자 응답 오류');
    const a = value as Record<string, unknown>;
    if (!integer(a.id) || a.id === 0 || !text(a.nickname) || !text(a.profileImage) || !(a.tag === null || typeof a.tag === 'string' && validProfileSlug(a.tag))) throw new Error('작성자 응답 오류');
    return { id: a.id, nickname: a.nickname, tag: a.tag as string | null, profileImage: a.profileImage };
}
export function parseLikedPostsPage(value: unknown): LikedPostsPage {
    if (!value || typeof value !== 'object') throw new Error('목록 응답 오류');
    const p = value as Record<string, unknown>;
    if (!integer(p.page) || p.page > 1000000 || !integer(p.size) || p.size < 1 || p.size > 100
        || !integer(p.totalElements) || !integer(p.totalPages) || p.totalPages !== Math.ceil(p.totalElements / p.size)
        || !Array.isArray(p.items) || p.items.length > p.size || p.items.length > p.totalElements) throw new Error('목록 응답 오류');
    const items: LikedPostItem[] = p.items.map(item => {
        if (!item || !integer(item.id) || item.id === 0 || !['POST', 'REPOST'].includes(item.type)
            || !text(item.category) || !preview(item.contentPreview) || !date(item.createdAt) || !date(item.likedAt)) throw new Error('게시글 응답 오류');
        let originalPost: LikedPostItem['originalPost'] = null;
        if (item.type === 'REPOST') {
            const o = item.originalPost;
            if (!o || !integer(o.id) || o.id === 0 || !preview(o.contentPreview)) throw new Error('원본 응답 오류');
            originalPost = { id: o.id, contentPreview: o.contentPreview, author: author(o.author) };
        } else if (item.originalPost !== null) throw new Error('원본 응답 오류');
        return { id: item.id, type: item.type, category: item.category, contentPreview: item.contentPreview,
            author: author(item.author), createdAt: item.createdAt, likedAt: item.likedAt, originalPost };
    });
    if (new Set(items.map(item => item.id)).size !== items.length) throw new Error('중복 게시글 응답');
    return { items, page: p.page, size: p.size, totalElements: p.totalElements, totalPages: p.totalPages };
}
export function likedPostsPagination(params: URLSearchParams): { page: number; size: number } | null {
    const parse = (key: string, fallback: number) => {
        const values = params.getAll(key);
        if (!values.length) return fallback;
        return values.length === 1 && /^(0|[1-9]\d*)$/.test(values[0]) ? Number(values[0]) : NaN;
    };
    const page = parse('page', 0), size = parse('size', 20);
    return integer(page) && page <= 1000000 && integer(size) && size >= 1 && size <= 100 ? { page, size } : null;
}
export async function fetchLikedPosts(page: number, size: number, signal?: AbortSignal): Promise<LikedPostsPage> {
    const response = await fetch(`/api/profiles/me/liked-posts?page=${page}&size=${size}`, { cache: 'no-store', signal });
    if (!response.ok) {
        const error = await response.json().catch(() => null);
        throw new ProfileSaveError(response.status, typeof error?.message === 'string' ? error.message : '좋아요한 글을 불러오지 못했어요.');
    }
    const result = parseLikedPostsPage(await response.json());
    if (result.page !== page || result.size !== size) throw new Error('요청한 페이지와 응답이 일치하지 않아요.');
    return result;
}

export function likedPostCategoryLabel(value: string | null): string {
    const labels: Record<string, string> = { INFO: '정보', FEEDBACK: '피드백', PROMO: '홍보', COMMUNITY: '자유' };
    return value ? labels[value] || value : '분류 없음';
}
