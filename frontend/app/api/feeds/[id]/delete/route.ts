import { feedError, feedRequestGuard, proxyFeed, routeId } from '@/lib/social/feedProxy';

type Context = { params: Promise<{ id: string }> };

// 8.6 게시글 삭제(논리 삭제). 작성자만 삭제할 수 있다.
export async function POST(request: Request, context: Context) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const id = await routeId(context);
    if (!id) return feedError(404, '게시글을 찾을 수 없어요.');
    return proxyFeed(`/api/v1/feeds/${id}/delete`, { method: 'POST' }, undefined, { 403: '본인 게시글만 삭제할 수 있어요.' });
}
