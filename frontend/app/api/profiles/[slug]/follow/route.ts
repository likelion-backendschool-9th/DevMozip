import { parseFollowResult, parseFollowStatus } from '@/lib/profiles/followApi';
import { followPath, followRequestGuard, followSlugError, proxyFollow } from '@/lib/profiles/followProxy';

type Context = { params: Promise<{ slug: string }> };

// 팔로우 상태: 비로그인도 조회한다. 로그인 쿠키가 있으면 요청자 기준 값이 채워진다.
export async function GET(_request: Request, context: Context) {
    const { slug } = await context.params;
    return followSlugError(slug) ?? proxyFollow(followPath(slug, 'follow'), {}, parseFollowStatus);
}

// 팔로우(9.1). 요청자는 백엔드가 토큰으로 정한다.
export async function POST(request: Request, context: Context) {
    const blocked = followRequestGuard(request);
    if (blocked) return blocked;
    const { slug } = await context.params;
    return followSlugError(slug) ?? proxyFollow(followPath(slug, 'follow'), { method: 'POST' }, parseFollowResult, undefined,
        { 400: '자기 자신은 팔로우할 수 없어요.' });
}

// 언팔로우(9.2)
export async function DELETE(request: Request, context: Context) {
    const blocked = followRequestGuard(request);
    if (blocked) return blocked;
    const { slug } = await context.params;
    return followSlugError(slug) ?? proxyFollow(followPath(slug, 'follow'), { method: 'DELETE' }, parseFollowResult, undefined,
        { 400: '자기 자신은 언팔로우할 수 없어요.' });
}
