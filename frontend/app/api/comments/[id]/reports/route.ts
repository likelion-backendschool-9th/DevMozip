import { parseFeedReport } from '@/lib/social/feedApi';
import { feedError, feedJsonBody, feedRequestGuard, proxyFeed, routeId } from '@/lib/social/feedProxy';

type Context = { params: Promise<{ id: string }> };

// 8.16 댓글 신고. 같은 계정은 한 번만 신고하고 본인 댓글은 신고할 수 없다.
export async function POST(request: Request, context: Context) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const id = await routeId(context);
    if (!id) return feedError(404, '댓글을 찾을 수 없어요.');
    const init = await feedJsonBody(request, ['reason']);
    if (init instanceof Response) return init;
    return proxyFeed(`/api/v1/comments/${id}/reports`, init, parseFeedReport, {
        400: '신고 사유를 확인해 주세요. 본인이 작성한 내용은 신고할 수 없어요.',
        404: '댓글을 찾을 수 없어요.',
        409: '이미 신고한 내용입니다.',
    });
}
