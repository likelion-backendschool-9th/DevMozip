import { parseFeedPost } from '@/lib/social/feedApi';
import { feedError, feedJsonBody, feedRequestGuard, proxyFeed, routeId } from '@/lib/social/feedProxy';

type Context = { params: Promise<{ id: string }> };

// 8.13 퍼가기. 의견(content)은 없어도 된다.
export async function POST(request: Request, context: Context) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const id = await routeId(context);
    if (!id) return feedError(404, '원본 글을 찾을 수 없어요.');
    const init = await feedJsonBody(request, ['content'], true);
    if (init instanceof Response) return init;
    return proxyFeed(`/api/v1/feeds/${id}/reposts`, init, parseFeedPost, {
        400: '퍼가기 의견은 1,000자 이내로 입력해 주세요.',
        404: '원본 글을 찾을 수 없어요.',
        409: '이미 내 프로필에 퍼간 글입니다.',
    });
}

// 8.13 내가 퍼간 글 삭제. 퍼간 글이 없어도 그대로 끝난다.
export async function DELETE(request: Request, context: Context) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const id = await routeId(context);
    if (!id) return feedError(404, '원본 글을 찾을 수 없어요.');
    return proxyFeed(`/api/v1/feeds/${id}/reposts`, { method: 'DELETE' });
}
