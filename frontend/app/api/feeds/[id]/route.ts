import { parseFeedPost } from '@/lib/social/feedApi';
import {
    FEED_POST_KEYS, FEED_UPLOAD_TIMEOUT_MS, feedError, feedMultipartBody, feedRequestGuard, proxyFeed, routeId,
} from '@/lib/social/feedProxy';

type Context = { params: Promise<{ id: string }> };

// 8.5 게시글 상세. view=true면 작성자 본인이 아닐 때 조회수를 1 올린다. 처음 읽는지는 화면이 localStorage로 정한다.
export async function GET(request: Request, context: Context) {
    const id = await routeId(context);
    if (!id) return feedError(404, '게시글을 찾을 수 없어요.');
    const params = new URL(request.url).searchParams;
    if ([...params.keys()].some(key => key !== 'view') || params.getAll('view').length > 1
        || (params.has('view') && params.get('view') !== 'true')) return feedError(400, '조회 조건을 확인해 주세요.');
    return proxyFeed(`/api/v1/feeds/${id}${params.has('view') ? '?view=true' : ''}`, {}, parseFeedPost);
}

// 8.6 게시글 수정. 퍼간 글이면 의견(content)만 바뀐다. keepImageUrls는 남길 기존 이미지다.
export async function PUT(request: Request, context: Context) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const id = await routeId(context);
    if (!id) return feedError(404, '게시글을 찾을 수 없어요.');
    const init = await feedMultipartBody(request, [...FEED_POST_KEYS, 'keepImageUrls']);
    if (init instanceof Response) return init;
    return proxyFeed(`/api/v1/feeds/${id}`, init, parseFeedPost,
        { 400: '입력 내용과 이미지를 확인해 주세요.', 403: '본인 게시글만 수정할 수 있어요.' }, FEED_UPLOAD_TIMEOUT_MS);
}
