import { parseFeedBookmark } from '@/lib/social/feedApi';
import { feedError, feedRequestGuard, proxyFeed, routeId } from '@/lib/social/feedProxy';

type Context = { params: Promise<{ id: string }> };

async function handle(request: Request, context: Context) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const id = await routeId(context);
    if (!id) return feedError(404, '게시글을 찾을 수 없어요.');
    return proxyFeed(`/api/v1/feeds/${id}/bookmark`, { method: request.method }, body => {
        const result = parseFeedBookmark(body);
        if (result.postId !== id || result.bookmarked !== (request.method === 'POST')) throw new Error('요청과 응답 불일치');
        return result;
    });
}

// 8.8 북마크 추가(POST)·해제(DELETE). 여러 번 보내도 최종 상태가 같다.
export const POST = handle;
export const DELETE = handle;
