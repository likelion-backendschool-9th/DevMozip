import { parseFeedComment, parseFeedComments } from '@/lib/social/feedApi';
import { feedError, feedJsonBody, feedRequestGuard, proxyFeed, routeId } from '@/lib/social/feedProxy';

type Context = { params: Promise<{ id: string }> };

// 8.9 댓글 목록. 비로그인도 조회한다.
export async function GET(_request: Request, context: Context) {
    const id = await routeId(context);
    if (!id) return feedError(404, '게시글을 찾을 수 없어요.');
    return proxyFeed(`/api/v1/feeds/${id}/comments`, {}, parseFeedComments);
}

// 8.10 댓글 작성
export async function POST(request: Request, context: Context) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const id = await routeId(context);
    if (!id) return feedError(404, '게시글을 찾을 수 없어요.');
    const init = await feedJsonBody(request, ['content']);
    if (init instanceof Response) return init;
    return proxyFeed(`/api/v1/feeds/${id}/comments`, init, parseFeedComment, { 400: '댓글은 1~1,000자로 입력해 주세요.' });
}
