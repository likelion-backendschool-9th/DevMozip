import type { ProfilePostAuthor, ProfilePostItem, ProfilePostsPage } from '@/types/profilePosts';
import { validProfileSlug } from './profileDetail';
const integer = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const text = (v: unknown): v is string | null => v === null || typeof v === 'string';
const preview = (v: unknown): v is string | null => text(v) && (v === null || v.length <= 600);
const date = (v: unknown): v is string | null => v === null || typeof v === 'string' && /^\d{4}-\d{2}-\d{2}T.*(?:Z|[+-]\d{2}:\d{2})$/.test(v) && Number.isFinite(Date.parse(v));
function author(value: unknown): ProfilePostAuthor {
    if (!value || typeof value !== 'object') throw new Error('작성자 응답 오류');
    const a = value as Record<string, unknown>;
    if (!integer(a.id) || a.id === 0 || !text(a.nickname) || !text(a.profileImage) || !(a.tag === null || typeof a.tag === 'string' && validProfileSlug(a.tag))) throw new Error('작성자 응답 오류');
    return { id: a.id, nickname: a.nickname, tag: a.tag as string | null, profileImage: a.profileImage };
}
export function parseProfilePostsPage(value: unknown): ProfilePostsPage {
    if (!value || typeof value !== 'object') throw new Error('목록 응답 오류');
    const p = value as Record<string, unknown>;
    if (!integer(p.page) || p.page > 1000000 || !integer(p.size) || p.size < 1 || p.size > 100
        || !integer(p.totalElements) || !integer(p.totalPages) || p.totalPages !== Math.ceil(p.totalElements / p.size)
        || !Array.isArray(p.items) || p.items.length > p.size || p.items.length > p.totalElements) throw new Error('목록 응답 오류');
    const items: ProfilePostItem[] = p.items.map(item => {
        if (!item || !integer(item.id) || item.id === 0 || !['POST', 'REPOST'].includes(item.type)
            || !text(item.category) || !preview(item.contentPreview) || !date(item.createdAt) || !text(item.title) || (typeof item.title === 'string' && item.title.length > 150)) throw new Error('게시글 응답 오류');
        let originalPost: ProfilePostItem['originalPost'] = null;
        if (item.type === 'REPOST') {
            const o = item.originalPost;
            if (!o || !integer(o.id) || o.id === 0 || !preview(o.contentPreview)) throw new Error('원본 응답 오류');
            originalPost = { id: o.id, contentPreview: o.contentPreview, author: author(o.author) };
        } else if (item.originalPost !== null) throw new Error('원본 응답 오류');
        return { id: item.id, type: item.type, category: item.category, title: item.title, contentPreview: item.contentPreview,
            author: author(item.author), createdAt: item.createdAt, originalPost };
    });
    if (new Set(items.map(item => item.id)).size !== items.length) throw new Error('중복 게시글 응답');
    return { items, page: p.page, size: p.size, totalElements: p.totalElements, totalPages: p.totalPages };
}

export async function fetchProfilePosts(target: string, page: number, signal?: AbortSignal): Promise<ProfilePostsPage> {
    const response = await fetch(`/api/profiles/${encodeURIComponent(target)}/posts?page=${page}&size=20`, { cache: 'no-store', signal });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(typeof body?.message === 'string' ? body.message : '포스팅을 불러오지 못했어요.');
    }
    const value = parseProfilePostsPage(await response.json());
    if (value.page !== page || value.size !== 20) throw new Error('요청한 페이지와 응답이 일치하지 않아요.');
    return value;
}
