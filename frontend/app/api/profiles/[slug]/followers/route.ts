import { proxyFollowList } from '@/lib/profiles/followProxy';

// 팔로워 목록(9.3). 비로그인도 조회한다.
export async function GET(request: Request, context: { params: Promise<{ slug: string }> }) {
    const { slug } = await context.params;
    return proxyFollowList(request, slug, 'followers');
}
