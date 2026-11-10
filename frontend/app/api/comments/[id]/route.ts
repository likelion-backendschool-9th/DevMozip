import { parseFeedComment } from '@/lib/social/feedApi';
import { feedError, feedJsonBody, feedRequestGuard, proxyFeed, routeId } from '@/lib/social/feedProxy';

type Context = { params: Promise<{ id: string }> };
const notFound = '댓글을 찾을 수 없어요. 삭제되었거나 게시글을 볼 수 없어요.';

// 8.11 댓글 수정. 작성자만 수정할 수 있다.
export async function PUT(request: Request, context: Context) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const id = await routeId(context);
    if (!id) return feedError(404, notFound);
    const init = await feedJsonBody(request, ['content']);
    if (init instanceof Response) return init;
    return proxyFeed(`/api/v1/comments/${id}`, init, parseFeedComment,
        { 400: '댓글은 1~1,000자로 입력해 주세요.', 403: '본인 댓글만 관리할 수 있어요.', 404: notFound });
}

// 8.12 댓글 삭제(논리 삭제)
export async function DELETE(request: Request, context: Context) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const id = await routeId(context);
    if (!id) return feedError(404, notFound);
    return proxyFeed(`/api/v1/comments/${id}`, { method: 'DELETE' }, undefined, { 403: '본인 댓글만 관리할 수 있어요.', 404: notFound });
}
