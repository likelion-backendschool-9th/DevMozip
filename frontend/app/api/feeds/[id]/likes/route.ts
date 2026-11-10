import { parseFeedLike } from '@/lib/social/feedApi';
import { feedError, feedRequestGuard, proxyFeed, routeId } from '@/lib/social/feedProxy';

type Context = { params: Promise<{ id: string }> };

// 8.7 좋아요·취소 토글. 응답은 처리 뒤의 최종 상태다.
export async function PATCH(request: Request, context: Context) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const id = await routeId(context);
    if (!id) return feedError(404, '게시글을 찾을 수 없어요.');
    return proxyFeed(`/api/v1/feeds/${id}/likes`, { method: 'PATCH' }, body => {
        const result = parseFeedLike(body);
        if (result.postId !== id) throw new Error('요청과 응답 불일치');
        return result;
    });
}
