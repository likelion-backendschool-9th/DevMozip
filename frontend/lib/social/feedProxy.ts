import 'server-only';
import { NextResponse } from 'next/server';
import { backendFetch } from '@/lib/auth/backend';
import type { FeedSortValue } from '@/types/feedApi';
import { FEED_IMAGE_MAX_COUNT, FEED_IMAGE_MESSAGE, feedListQuery, feedListSearch, feedPostId, parseFeedPage, validFeedImage } from './feedApi';

const headers = { 'Cache-Control': 'no-store' };

export function feedError(status: number, message: string) {
    return NextResponse.json({ message }, { status, headers });
}

// 백엔드 오류 본문은 그대로 넘기지 않고 상태별 문구만 보낸다. 경로마다 다른 문구는 proxyFeed의 overrides로 준다.
const messages: Record<number, string> = {
    400: '입력 내용을 확인해 주세요.',
    401: '로그인이 필요해요. 다시 로그인해 주세요.',
    403: '본인이 작성한 글만 처리할 수 있어요.',
    404: '게시글을 찾을 수 없어요. 삭제되었거나 숨김 처리된 글일 수 있어요.',
    409: '이미 처리된 요청이에요.',
    503: '이미지를 저장하지 못했어요. 잠시 후 다시 시도해 주세요.',
};

/** 쓰기 요청은 같은 사이트에서만 받는다. 프로젝트·팔로우 BFF와 같이 브라우저가 보낸 Host와 비교한다. */
export function feedRequestGuard(request: Request) {
    const url = new URL(request.url);
    let origin: URL;
    try { origin = new URL(request.headers.get('origin') ?? ''); }
    catch { return feedError(403, '같은 사이트에서 요청해 주세요.'); }
    if (origin.host !== (request.headers.get('host') ?? url.host) || origin.protocol !== url.protocol)
        return feedError(403, '같은 사이트에서 요청해 주세요.');
    return null;
}

/** 경로의 게시글·댓글 id. 형식이 틀리면 null이다. */
export async function routeId(context: { params: Promise<{ id: string }> }) {
    return feedPostId((await context.params).id);
}

export async function proxyFeed<T>(path: string, init: RequestInit, parse?: (body: unknown) => T,
    overrides: Record<number, string> = {}, timeoutMs = 5000) {
    let response: Response;
    try { response = await backendFetch(path, { ...init, signal: AbortSignal.timeout(timeoutMs) }); }
    catch {
        return feedError(502, init.method && init.method !== 'GET'
            ? '서버 응답을 확인하지 못했어요. 다시 시도하기 전에 저장되었는지 확인해 주세요.'
            : '피드 서버에 연결하지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
    if (!response.ok) {
        const message = overrides[response.status] ?? messages[response.status];
        return feedError(message ? response.status : 502, message ?? '피드 서버 요청을 완료하지 못했어요.');
    }
    if (response.status === 204 || !parse) return new Response(null, { status: 204, headers });
    try { return NextResponse.json(parse(await response.json()), { status: response.status, headers }); }
    catch { return feedError(502, '피드 서버의 응답 형식을 확인할 수 없어요.'); }
}

/** 피드 목록 BFF(8.1·8.2). 조건을 확인한 값만 백엔드로 보낸다. */
export async function proxyFeedList(request: Request, path: string, defaultSort: FeedSortValue) {
    const query = feedListQuery(new URL(request.url).searchParams, defaultSort);
    if (!query) return feedError(400, '조회 조건을 확인해 주세요.');
    return proxyFeed(`${path}?${feedListSearch(query)}`, {}, body => {
        const page = parseFeedPage(body);
        if (page.page !== query.page || page.size !== query.size) throw new Error('요청과 응답 불일치');
        return page;
    }, { 400: '조회 조건을 확인해 주세요.', 404: '피드를 찾을 수 없어요.' });
}

function pick(value: unknown, keys: string[]) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('입력 형식 오류');
    const source = value as Record<string, unknown>;
    return Object.fromEntries(keys.filter(key => key in source).map(key => [key, source[key]]));
}

/** JSON 본문에서 허용한 값만 백엔드로 보낸다. 사용자 ID 같은 다른 값은 버린다. optional이면 본문 없는 요청도 받는다. */
export async function feedJsonBody(request: Request, keys: string[], optional = false): Promise<RequestInit | NextResponse> {
    const type = request.headers.get('content-type') ?? '';
    if (!type && optional) return { method: request.method };
    if (!type.toLowerCase().startsWith('application/json')) return feedError(415, 'JSON 형식이 필요해요.');
    try {
        const body = pick(await request.json(), keys);
        return { method: request.method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
    } catch { return feedError(400, '입력 내용을 확인해 주세요.'); }
}

/**
 * 작성·수정의 multipart 본문을 확인하고 다시 만든다. 글 내용은 JSON 파트 "post", 이미지는 파일 파트 "images"다.
 * 이미지는 형식·크기·개수만 먼저 확인하고, 파일 내용 검사는 백엔드가 한다.
 */
export async function feedMultipartBody(request: Request, keys: string[]): Promise<RequestInit | NextResponse> {
    if (!(request.headers.get('content-type') ?? '').toLowerCase().startsWith('multipart/form-data')) {
        return feedError(415, 'multipart/form-data 형식이 필요해요.');
    }
    let form: FormData;
    try { form = await request.formData(); }
    catch { return feedError(400, '입력 내용을 확인해 주세요.'); }
    if ([...form.keys()].some(key => key !== 'post' && key !== 'images')) return feedError(400, '입력 내용을 확인해 주세요.');
    const posts = form.getAll('post');
    if (posts.length !== 1) return feedError(400, '게시글 내용을 확인해 주세요.');
    let post: Record<string, unknown>;
    try {
        const raw = posts[0];
        post = pick(JSON.parse(typeof raw === 'string' ? raw : await raw.text()), keys);
    } catch { return feedError(400, '게시글 내용을 확인해 주세요.'); }
    const images = form.getAll('images');
    if (images.length > FEED_IMAGE_MAX_COUNT || images.some(image => typeof image === 'string' || !validFeedImage(image))) {
        return feedError(400, FEED_IMAGE_MESSAGE);
    }
    const body = new FormData();
    body.append('post', new Blob([JSON.stringify(post)], { type: 'application/json' }));
    // 원래 파일 이름은 서버가 쓰지 않으므로 순서만 담은 이름으로 보낸다.
    (images as File[]).forEach((image, index) => body.append('images', image, `image-${index + 1}`));
    return { method: request.method, body };
}

export const FEED_POST_KEYS = ['category', 'title', 'content', 'techStacks', 'interests'];
// 업로드는 이미지 3장(최대 1.5MB)을 함께 보내므로 기본 5초보다 길게 기다린다.
export const FEED_UPLOAD_TIMEOUT_MS = 30_000;
