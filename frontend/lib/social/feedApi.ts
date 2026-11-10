import type {
    FeedAuthor, FeedBookmarkResult, FeedCategoryCode, FeedComment, FeedLikeResult, FeedListMode, FeedListQuery,
    FeedMyRepost, FeedOriginal, FeedPage, FeedPost, FeedPostDraft, FeedReportResult, FeedSortValue,
} from '@/types/feedApi';

// SNS 피드 API(docs/feed-api.md) 응답 해석과 브라우저 요청. 브라우저는 BFF(/api/feeds, /api/comments)만 부르고 사용자 ID를 보내지 않는다.

export const FEED_PAGE_SIZE = 20;
export const FEED_MAX_SIZE = 100;
export const FEED_QUERY_MAX = 100;
export const FEED_TITLE_MAX = 150;
export const FEED_CONTENT_MAX = 10_000;
export const FEED_OPINION_MAX = 1_000;
export const FEED_COMMENT_MAX = 1_000;
export const FEED_TAG_MAX = 30;
export const FEED_TAG_LENGTH_MAX = 30;
export const FEED_IMAGE_MAX_COUNT = 3;
export const FEED_IMAGE_MAX_BYTES = 512 * 1024;
export const FEED_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp', 'image/gif'];
export const FEED_IMAGE_MESSAGE = '이미지는 PNG/JPEG/WebP/GIF, 각 512KB 이하, 최대 3장까지 첨부할 수 있습니다.';
// 백엔드 PostReportReason의 문구. 화면 선택지와 저장 값이 같다.
export const FEED_REPORT_REASONS = ['스팸 또는 광고', '욕설 또는 혐오 표현', '허위 정보', '저작권 침해', '기타'] as const;
// 읽은 게시글 id. 처음 읽을 때만 조회수 집계를 요청한다 (docs/feed-plan.md 4절 조회수)
export const FEED_VIEWS_KEY = 'devmozip:feed-views:v1';

export const FEED_CATEGORIES: { code: FeedCategoryCode; label: string }[] = [
    { code: 'INFO', label: '정보' },
    { code: 'FEEDBACK', label: '피드백' },
    { code: 'PROMO', label: '홍보' },
    { code: 'COMMUNITY', label: '자유' },
];
const SORTS: FeedSortValue[] = ['recommended', 'popular', 'views', 'latest'];

export function categoryLabel(code: FeedCategoryCode | null | undefined) {
    return FEED_CATEGORIES.find(item => item.code === code)?.label ?? '';
}

/** 화면 필터의 한글 문구(주소의 ?category=정보)를 코드로 바꾼다. 전체·모르는 값은 null(조건 없음)이다. */
export function categoryCode(label: string | null | undefined): FeedCategoryCode | null {
    return FEED_CATEGORIES.find(item => item.label === label)?.code ?? null;
}

// ---- 응답 해석 ----

const id = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v > 0;
const count = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0;
const flag = (v: unknown): v is boolean => typeof v === 'boolean';
const text = (v: unknown): v is string | null => v === null || typeof v === 'string';
const strings = (v: unknown): v is string[] => Array.isArray(v) && v.every(s => typeof s === 'string');
const time = (v: unknown): v is string => typeof v === 'string' && Number.isFinite(Date.parse(v));
const isCategory = (v: unknown): v is FeedCategoryCode => FEED_CATEGORIES.some(item => item.code === v);
// 이미지는 서버가 저장한 /images/ 경로만 표시한다. 다른 주소나 상위 경로가 섞이면 응답을 버린다.
const imagePath = (v: unknown): v is string => typeof v === 'string' && /^\/images\/[A-Za-z0-9_-]+\.(png|jpg|webp|gif)$/.test(v);
const record = (v: unknown) => {
    if (!v || typeof v !== 'object' || Array.isArray(v)) throw new Error('피드 응답 형식 오류');
    return v as Record<string, unknown>;
};

function parseAuthor(value: unknown): FeedAuthor {
    const v = record(value);
    if (!id(v.id) || !text(v.nickname) || !text(v.tag) || !text(v.profileImage) || !strings(v.jobCategories)) throw new Error('작성자 응답 오류');
    // 알 수 없는 추가 필드(이메일 등)는 브라우저 응답에 넣지 않는다.
    return { id: v.id, nickname: v.nickname, tag: v.tag, profileImage: v.profileImage, jobCategories: v.jobCategories };
}

function parseOriginal(value: unknown): FeedOriginal {
    const v = record(value);
    if (!id(v.id) || !flag(v.available)) throw new Error('원본 글 응답 오류');
    if (!v.available) return { id: v.id, available: false, category: null, title: null, author: null };
    if (!isCategory(v.category) || !text(v.title)) throw new Error('원본 글 응답 오류');
    return { id: v.id, available: true, category: v.category, title: v.title, author: parseAuthor(v.author) };
}

function parseMyRepost(value: unknown): FeedMyRepost {
    const v = record(value);
    if (!id(v.id) || !text(v.content)) throw new Error('퍼간 글 응답 오류');
    return { id: v.id, content: v.content };
}

export function parseFeedPost(value: unknown): FeedPost {
    const v = record(value);
    const repost = v.type === 'REPOST';
    if (!id(v.id) || (v.type !== 'POST' && !repost) || !isCategory(v.category) || !text(v.title) || !text(v.content)
        || !strings(v.techStacks) || !strings(v.interests) || !Array.isArray(v.imageUrls) || !v.imageUrls.every(imagePath)
        || !count(v.viewCount) || !count(v.likeCount) || !count(v.commentCount)
        || !['VISIBLE', 'HIDDEN', 'RESTRICTED'].includes(v.visibility as string) || !time(v.createdAt) || !time(v.updatedAt)
        || !flag(v.edited) || !flag(v.mine) || !flag(v.liked) || !flag(v.bookmarked) || !flag(v.reported)
        || (repost ? v.original === null || v.original === undefined : v.original !== null && v.original !== undefined)
        || (repost && v.myRepost !== null && v.myRepost !== undefined)) throw new Error('게시글 응답 오류');
    return {
        id: v.id, type: v.type as FeedPost['type'], category: v.category, title: v.title, content: v.content,
        techStacks: v.techStacks, interests: v.interests, imageUrls: v.imageUrls as string[], viewCount: v.viewCount,
        likeCount: v.likeCount, commentCount: v.commentCount, visibility: v.visibility as FeedPost['visibility'],
        author: parseAuthor(v.author), original: repost ? parseOriginal(v.original) : null,
        createdAt: v.createdAt, updatedAt: v.updatedAt, edited: v.edited, mine: v.mine, liked: v.liked,
        bookmarked: v.bookmarked, reported: v.reported,
        myRepost: v.myRepost === null || v.myRepost === undefined ? null : parseMyRepost(v.myRepost),
    };
}

export function parseFeedPage(value: unknown): FeedPage {
    const v = record(value);
    if (!count(v.page) || !count(v.size) || v.size < 1 || v.size > FEED_MAX_SIZE || !flag(v.hasNext) || !count(v.totalCount)
        || !Array.isArray(v.items) || v.items.length > v.size) throw new Error('피드 목록 응답 오류');
    const items = v.items.map(parseFeedPost);
    if (new Set(items.map(item => item.id)).size !== items.length) throw new Error('중복 게시글 응답');
    return { items, page: v.page, size: v.size, hasNext: v.hasNext, totalCount: v.totalCount };
}

export function parseFeedComment(value: unknown): FeedComment {
    const v = record(value);
    if (!id(v.id) || !id(v.postId) || typeof v.content !== 'string' || !time(v.createdAt) || !time(v.updatedAt)
        || !flag(v.edited) || !flag(v.mine) || !flag(v.reported)) throw new Error('댓글 응답 오류');
    return { id: v.id, postId: v.postId, content: v.content, author: parseAuthor(v.author), createdAt: v.createdAt,
        updatedAt: v.updatedAt, edited: v.edited, mine: v.mine, reported: v.reported };
}

export function parseFeedComments(value: unknown): FeedComment[] {
    if (!Array.isArray(value)) throw new Error('댓글 목록 응답 오류');
    return value.map(parseFeedComment);
}

export function parseFeedLike(value: unknown): FeedLikeResult {
    const v = record(value);
    if (!id(v.postId) || !flag(v.liked) || !count(v.likeCount) || (v.liked && v.likeCount === 0)) throw new Error('좋아요 응답 오류');
    return { postId: v.postId, liked: v.liked, likeCount: v.likeCount };
}

export function parseFeedBookmark(value: unknown): FeedBookmarkResult {
    const v = record(value);
    if (!id(v.postId) || !flag(v.bookmarked)) throw new Error('북마크 응답 오류');
    return { postId: v.postId, bookmarked: v.bookmarked };
}

export function parseFeedReport(value: unknown): FeedReportResult {
    const v = record(value);
    if (!id(v.id) || (v.targetType !== 'POST' && v.targetType !== 'COMMENT') || !id(v.targetId) || typeof v.status !== 'string'
        || !time(v.createdAt)) throw new Error('신고 응답 오류');
    return { id: v.id, targetType: v.targetType, targetId: v.targetId, status: v.status, createdAt: v.createdAt };
}

// ---- 목록 조건 ----

/** BFF가 받은 목록 조건을 확인한다. 같은 이름이 두 번 오거나 형식이 틀리면 null이다. */
export function feedListQuery(params: URLSearchParams, defaultSort: FeedSortValue): FeedListQuery | null {
    const allowed = ['category', 'q', 'sort', 'page', 'size', 'seed'];
    if ([...params.keys()].some(key => !allowed.includes(key))) return null;
    const single = (key: string) => {
        const values = params.getAll(key);
        return values.length > 1 ? null : values[0];
    };
    const number = (key: string, fallback: number) => {
        const raw = single(key);
        if (raw === undefined) return fallback;
        return raw !== null && /^(0|[1-9]\d{0,8})$/.test(raw) ? Number(raw) : NaN;
    };
    const category = single('category'), q = single('q'), sort = single('sort');
    const page = number('page', 0), size = number('size', FEED_PAGE_SIZE), seed = number('seed', 0);
    if (category === null || (category !== undefined && category !== '' && !isCategory(category))) return null;
    if (q === null || (q ?? '').length > FEED_QUERY_MAX) return null;
    if (sort === null || (sort !== undefined && !SORTS.includes(sort as FeedSortValue))) return null;
    if (!count(page) || page > 1_000_000 || !count(size) || size < 1 || size > FEED_MAX_SIZE || !count(seed)) return null;
    return { category: category ? category as FeedCategoryCode : null, q: (q ?? '').trim(), sort: (sort as FeedSortValue | undefined) ?? defaultSort,
        page, size, seed };
}

export function feedListSearch(query: FeedListQuery): string {
    const params = new URLSearchParams();
    if (query.category) params.set('category', query.category);
    if (query.q.trim()) params.set('q', query.q.trim());
    params.set('sort', query.sort);
    params.set('page', String(query.page));
    params.set('size', String(query.size));
    params.set('seed', String(query.seed));
    return params.toString();
}

/** 다음 페이지를 붙일 때 그사이 새 글이 생겨 다시 온 글(같은 id)은 넣지 않는다. */
export function appendFeedPosts(current: FeedPost[], next: FeedPost[]): FeedPost[] {
    const ids = new Set(current.map(item => item.id));
    return [...current, ...next.filter(item => !ids.has(item.id))];
}

// ---- 작성 입력 ----

function validTags(values: string[]) {
    const trimmed = values.map(value => value.trim());
    return values.length <= FEED_TAG_MAX && trimmed.every(value => value.length > 0 && value.length <= FEED_TAG_LENGTH_MAX)
        && new Set(trimmed.map(value => value.toLowerCase())).size === trimmed.length;
}

export function validFeedImage(file: Pick<File, 'type' | 'size'>) {
    return FEED_IMAGE_TYPES.includes(file.type) && file.size > 0 && file.size <= FEED_IMAGE_MAX_BYTES;
}

/** 서버(FeedRules)와 같은 기준·문구로 먼저 확인한다. */
export function validateFeedDraft(draft: FeedPostDraft): string[] {
    const errors: string[] = [];
    if (!draft.title.trim() || draft.title.trim().length > FEED_TITLE_MAX) errors.push('제목을 1~150자로 입력해 주세요.');
    if (!draft.content.trim() || draft.content.length > FEED_CONTENT_MAX) errors.push('본문을 1~10,000자로 입력해 주세요.');
    if (!isCategory(draft.category)) errors.push('카테고리를 선택해 주세요.');
    if (!validTags(draft.techStacks)) errors.push('기술 스택 태그는 중복 없이 1~30자로 최대 30개까지 입력해 주세요.');
    if (!validTags(draft.interests)) errors.push('관심 분야는 중복 없이 1~30자로 최대 30개까지 입력해 주세요.');
    if (draft.keepImageUrls.length + draft.files.length > FEED_IMAGE_MAX_COUNT || !draft.files.every(validFeedImage)) errors.push(FEED_IMAGE_MESSAGE);
    return errors;
}

/** 작성·수정 요청 본문. 글 내용은 JSON 파트 "post", 이미지는 파일 파트 "images"다. */
export function feedDraftForm(draft: FeedPostDraft, editing: boolean): FormData {
    const post: Record<string, unknown> = {
        category: draft.category, title: draft.title.trim(), content: draft.content,
        techStacks: draft.techStacks.map(value => value.trim()), interests: draft.interests.map(value => value.trim()),
    };
    if (editing) post.keepImageUrls = draft.keepImageUrls;
    const form = new FormData();
    form.append('post', new Blob([JSON.stringify(post)], { type: 'application/json' }));
    for (const file of draft.files) form.append('images', file, file.name);
    return form;
}

// ---- 조회수 기록 (localStorage) ----

type ViewStorage = Pick<Storage, 'getItem' | 'setItem'>;

export function readFeedViews(storage: ViewStorage | undefined): Set<number> {
    try {
        const value = JSON.parse(storage?.getItem(FEED_VIEWS_KEY) ?? '[]');
        return new Set(Array.isArray(value) ? value.filter(id) : []);
    } catch { return new Set(); }
}

/** 처음 읽는 글이면 true다. localStorage를 쓸 수 없으면 들어올 때마다 센다(docs/feed-plan.md 4절 한계). */
export function isFirstFeedView(storage: ViewStorage | undefined, postId: number) {
    return !readFeedViews(storage).has(postId);
}

export function recordFeedView(storage: ViewStorage | undefined, postId: number) {
    try {
        const views = readFeedViews(storage);
        if (views.has(postId)) return;
        views.add(postId);
        storage?.setItem(FEED_VIEWS_KEY, JSON.stringify([...views]));
    } catch { /* 저장하지 못해도 화면은 그대로 쓴다. */ }
}

export function browserStorage(): ViewStorage | undefined {
    try { return typeof localStorage === 'undefined' ? undefined : localStorage; }
    catch { return undefined; }
}

// ---- 브라우저 요청 ----

export class FeedRequestError extends Error {
    constructor(public readonly status: number, message: string) { super(message); }
}

async function request<T>(path: string, init: RequestInit, parse: (value: unknown) => T, fallback: string): Promise<T> {
    let response: Response;
    try { response = await fetch(path, { cache: 'no-store', credentials: 'same-origin', ...init }); }
    catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') throw error;
        throw new FeedRequestError(502, '서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new FeedRequestError(response.status, typeof body?.message === 'string' ? body.message : fallback);
    }
    if (response.status === 204) return parse(undefined);
    let body: unknown;
    try { body = await response.json(); }
    catch { throw new FeedRequestError(502, fallback); }
    try { return parse(body); }
    catch { throw new FeedRequestError(502, '서버 응답 형식을 확인할 수 없어요.'); }
}

const json = (method: string, body: unknown): RequestInit => ({ method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
const none = () => undefined;
const listPath: Record<FeedListMode, string> = { all: '/api/feeds', recommended: '/api/feeds/recommands', following: '/api/feeds/following' };

export async function fetchFeedPage(mode: FeedListMode, query: FeedListQuery, signal?: AbortSignal) {
    const page = await request(`${listPath[mode]}?${feedListSearch(query)}`, { signal }, parseFeedPage, '피드를 불러오지 못했어요.');
    if (page.page !== query.page || page.size !== query.size) throw new FeedRequestError(502, '요청한 페이지와 응답이 일치하지 않아요.');
    return page;
}

export function fetchFeedPost(postId: number, view: boolean, signal?: AbortSignal) {
    return request(`/api/feeds/${postId}${view ? '?view=true' : ''}`, { signal }, parseFeedPost, '게시글을 불러오지 못했어요.');
}

const viewing = new Map<number, Promise<FeedPost>>();

/**
 * 상세 화면용 조회. 처음 읽는 글이면 view=true로 요청하고, 성공하면 읽은 글로 기록한다.
 * 같은 글을 동시에 여러 번 열어도(개발 모드에서 effect가 두 번 실행되는 경우 등) 조회수 요청은 하나로 합친다.
 * 조회수 요청은 취소하지 않으므로, 화면은 결과를 받을 때 아직 그 글을 보고 있는지 확인한다.
 */
export function readFeedPost(postId: number, storage: ViewStorage | undefined, signal?: AbortSignal): Promise<FeedPost> {
    if (!isFirstFeedView(storage, postId)) return fetchFeedPost(postId, false, signal);
    const current = viewing.get(postId);
    if (current) return current;
    const pending = fetchFeedPost(postId, true).then(post => {
        recordFeedView(storage, postId);
        return post;
    }).finally(() => viewing.delete(postId));
    viewing.set(postId, pending);
    return pending;
}

export function saveFeedPost(draft: FeedPostDraft, postId?: number) {
    return request(postId ? `/api/feeds/${postId}` : '/api/feeds', { method: postId ? 'PUT' : 'POST', body: feedDraftForm(draft, !!postId) },
        parseFeedPost, '게시글을 저장하지 못했어요. 입력 내용은 그대로 있어요.');
}

export function deleteFeedPost(postId: number) {
    return request(`/api/feeds/${postId}/delete`, { method: 'POST' }, none, '게시글을 삭제하지 못했어요.');
}

export function toggleFeedLike(postId: number) {
    return request(`/api/feeds/${postId}/likes`, { method: 'PATCH' }, parseFeedLike, '좋아요를 반영하지 못했어요.');
}

export function changeFeedBookmark(postId: number, bookmarked: boolean) {
    return request(`/api/feeds/${postId}/bookmark`, { method: bookmarked ? 'POST' : 'DELETE' }, parseFeedBookmark, '북마크를 반영하지 못했어요.');
}

export function repostFeed(postId: number, content: string) {
    return request(`/api/feeds/${postId}/reposts`, json('POST', { content }), parseFeedPost, '퍼가지 못했어요.');
}

export function cancelFeedRepost(postId: number) {
    return request(`/api/feeds/${postId}/reposts`, { method: 'DELETE' }, none, '퍼가기를 취소하지 못했어요.');
}

/** 퍼간 글의 의견 수정은 퍼간 글의 id로 8.6 수정을 부른다. */
export function updateRepostOpinion(repostId: number, content: string) {
    const form = new FormData();
    form.append('post', new Blob([JSON.stringify({ content })], { type: 'application/json' }));
    return request(`/api/feeds/${repostId}`, { method: 'PUT', body: form }, parseFeedPost, '의견을 수정하지 못했어요.');
}

export function reportFeedPost(postId: number, reason: string) {
    return request(`/api/feeds/${postId}/reports`, json('POST', { reason }), parseFeedReport, '신고하지 못했어요.');
}

export function fetchFeedComments(postId: number, signal?: AbortSignal) {
    return request(`/api/feeds/${postId}/comments`, { signal }, parseFeedComments, '댓글을 불러오지 못했어요.');
}

export function createFeedComment(postId: number, content: string) {
    return request(`/api/feeds/${postId}/comments`, json('POST', { content }), parseFeedComment, '댓글을 등록하지 못했어요.');
}

export function updateFeedComment(commentId: number, content: string) {
    return request(`/api/comments/${commentId}`, json('PUT', { content }), parseFeedComment, '댓글을 수정하지 못했어요.');
}

export function deleteFeedComment(commentId: number) {
    return request(`/api/comments/${commentId}`, { method: 'DELETE' }, none, '댓글을 삭제하지 못했어요.');
}

export function reportFeedComment(commentId: number, reason: string) {
    return request(`/api/comments/${commentId}/reports`, json('POST', { reason }), parseFeedReport, '신고하지 못했어요.');
}

export function feedAuthorName(author: Pick<FeedAuthor, 'nickname'> | null | undefined) {
    return author?.nickname?.trim() || '작성자';
}

/** 프로필 주소. 태그가 없으면 링크를 두지 않는다. */
export function feedAuthorHref(author: Pick<FeedAuthor, 'tag'> | null | undefined) {
    return author?.tag ? `/users/${encodeURIComponent(author.tag)}` : null;
}

export function feedPostId(value: string | undefined) {
    return value && /^[1-9]\d{0,15}$/.test(value) ? Number(value) : null;
}
