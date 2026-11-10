import type { PortfolioDetail, PortfolioPage, PortfolioSummary } from '@/types/portfolioApi';
import { parsePortfolioDocument } from './portfolioDocument';
import { profileImageUrl, validProfileSlug } from '@/lib/profiles/profileDetail';
export const validPortfolioId = (id: string) => /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id));
const object = (v: unknown): Record<string, unknown> => { if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('응답 형식 오류'); return v as Record<string, unknown>; };
const integer = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const identifier = (v: unknown): v is number => integer(v) && v > 0;
function text(v: unknown, max = 200000): string | null { if (v === null || typeof v === 'string' && v.length <= max) return v; throw new Error('텍스트 응답 오류'); }
function date(v: unknown): string | null {
    if (v === null) return null;
    if (typeof v !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})?$/.test(v)
        || !Number.isFinite(Date.parse(v))) throw new Error('날짜 응답 오류');
    return v;
}
export function portfolioDate(v: string) { return new Date(/(?:Z|[+-]\d{2}:\d{2})$/.test(v) ? v : `${v}+09:00`).toLocaleDateString('ko-KR', { timeZone: 'Asia/Seoul' }); }
export function parsePortfolioSummary(value: unknown): PortfolioSummary {
    const v = object(value);
    if (!identifier(v.id) || !identifier(v.userId) || !['PUBLIC', 'PRIVATE'].includes(v.visibility as string)
        || !Array.isArray(v.techStack) || v.techStack.length > 100 || !v.techStack.every(x => typeof x === 'string' && x.trim() && x.length <= 100)
        || new Set(v.techStack).size !== v.techStack.length) throw new Error('포트폴리오 기본 응답 오류');
    return { id: v.id, userId: v.userId, title: text(v.title, 255), thumbnailUrl: profileImageUrl(text(v.thumbnailUrl, 2048)) || null,
        topic: text(v.topic, 255), summary: text(v.summary), role: text(v.role, 100), techStack: v.techStack as string[],
        visibility: v.visibility as PortfolioSummary['visibility'], createdAt: date(v.createdAt), updatedAt: date(v.updatedAt) };
}
export function parsePortfolioPage(value: unknown): PortfolioPage {
    const v = object(value);
    if (!integer(v.page) || v.page > 1000000 || !integer(v.size) || v.size < 1 || v.size > 100
        || !integer(v.totalElements) || !integer(v.totalPages) || v.totalPages !== Math.ceil(v.totalElements / v.size)
        || !Array.isArray(v.items) || v.items.length > v.size || v.items.length > v.totalElements) throw new Error('포트폴리오 목록 응답 오류');
    const items = v.items.map(parsePortfolioSummary);
    if (new Set(items.map(x => x.id)).size !== items.length || new Set(items.map(x => x.userId)).size > 1) throw new Error('목록 식별자 오류');
    return { items, page: v.page, size: v.size, totalElements: v.totalElements, totalPages: v.totalPages };
}
export function parsePortfolioDetail(value: unknown): PortfolioDetail {
    const v = object(value), author = object(v.author), portfolio = parsePortfolioSummary(v.portfolio);
    if (!identifier(author.id) || author.id !== portfolio.userId || !(author.tag === null || typeof author.tag === 'string' && validProfileSlug(author.tag))
        || !Array.isArray(v.attachments) || v.attachments.length > 100) throw new Error('포트폴리오 상세 응답 오류');
    const attachments = v.attachments.map(item => { const a = object(item); return { name: text(a.name, 255), url: profileImageUrl(text(a.url, 2048)) || '' }; }).filter(x => x.url);
    if (v.canEdit !== undefined && typeof v.canEdit !== 'boolean') throw new Error('편집 권한 응답 오류');
    const document = v.document == null ? null : parsePortfolioDocument(v.document);
    return { portfolio, canEdit: v.canEdit === true, document, author: { id: author.id, nickname: text(author.nickname, 50), tag: author.tag as string | null,
        profileImage: profileImageUrl(text(author.profileImage, 255)) || null }, description: text(v.description), attachments };
}
export function portfolioPagination(params: URLSearchParams): { page: number; size: number } | null {
    const number = (key: string, fallback: number) => { const v = params.getAll(key); return !v.length ? fallback : v.length === 1 && /^(0|[1-9]\d*)$/.test(v[0]) ? Number(v[0]) : NaN; };
    const page = number('page', 0), size = number('size', 20);
    return integer(page) && page <= 1000000 && integer(size) && size >= 1 && size <= 100 ? { page, size } : null;
}
export class PortfolioRequestError extends Error { constructor(public readonly status: number, message: string) { super(message); } }
async function get(path: string, signal?: AbortSignal): Promise<unknown> {
    const response = await fetch(path, { cache: 'no-store', signal });
    if (!response.ok) { const body = await response.json().catch(() => null); throw new PortfolioRequestError(response.status, typeof body?.message === 'string' ? body.message : '포트폴리오를 불러오지 못했어요.'); }
    return response.json();
}
export async function fetchPortfolioPage(slug: string, page: number, signal?: AbortSignal) {
    const value = parsePortfolioPage(await get(`/api/profiles/${encodeURIComponent(slug)}/portfolios?page=${page}&size=20`, signal));
    if (value.page !== page || value.size !== 20 || slug !== 'me' && value.items.some(x => x.visibility !== 'PUBLIC')) throw new Error('요청한 목록과 응답이 일치하지 않아요.');
    return value;
}
export async function fetchPortfolioDetail(id: string, signal?: AbortSignal) {
    if (!validPortfolioId(id)) throw new PortfolioRequestError(400, '포트폴리오 ID를 확인해 주세요.');
    const value = parsePortfolioDetail(await get(`/api/portfolios/${id}`, signal));
    if (value.portfolio.id !== Number(id)) throw new Error('요청한 포트폴리오와 응답이 일치하지 않아요.');
    return value;
}
