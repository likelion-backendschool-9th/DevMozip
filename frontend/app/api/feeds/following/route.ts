import { proxyFeedList } from '@/lib/social/feedProxy';

// 8.2 팔로잉 피드. 로그인이 필요하다(백엔드가 401로 응답한다).
export async function GET(request: Request) {
    return proxyFeedList(request, '/api/v1/feeds/following', 'latest');
}
