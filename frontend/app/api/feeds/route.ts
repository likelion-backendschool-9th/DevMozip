import { parseFeedPost } from '@/lib/social/feedApi';
import { FEED_POST_KEYS, FEED_UPLOAD_TIMEOUT_MS, feedMultipartBody, feedRequestGuard, proxyFeed, proxyFeedList } from '@/lib/social/feedProxy';

// 8.1 전체 피드. 비로그인도 조회하고, 로그인 쿠키가 있으면 요청자 기준 값(좋아요 여부 등)이 채워진다.
export async function GET(request: Request) {
    return proxyFeedList(request, '/api/v1/feeds', 'latest');
}

// 8.4 게시글 작성. 작성자는 백엔드가 토큰으로 정한다.
export async function POST(request: Request) {
    const blocked = feedRequestGuard(request);
    if (blocked) return blocked;
    const init = await feedMultipartBody(request, FEED_POST_KEYS);
    if (init instanceof Response) return init;
    return proxyFeed('/api/v1/feeds', init, parseFeedPost, { 400: '입력 내용과 이미지를 확인해 주세요.' }, FEED_UPLOAD_TIMEOUT_MS);
}
