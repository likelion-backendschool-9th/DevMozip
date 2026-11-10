import { proxyFeedList } from '@/lib/social/feedProxy';

// 8.2 추천 피드. 초기에는 seed로 섞은 랜덤 추천이다. 비로그인도 조회한다.
export async function GET(request: Request) {
    return proxyFeedList(request, '/api/v1/feeds/recommands', 'recommended');
}
