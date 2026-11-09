import { expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { LikedPostsResult, type LikedPostsResultState } from './LikedPostsResult';
const render = (result: LikedPostsResultState) => renderToStaticMarkup(<LikedPostsResult result={result} onRetry={() => {}} onPage={() => {}} />);
it('조회 중·실패·빈 목록을 구별한다', () => {
    expect(render({ status: 'loading' })).toContain('role="status"');
    const error = render({ status: 'error', code: 503, message: '서버를 확인해 주세요.' });
    expect(error).toContain('다시 시도'); expect(error).not.toContain('아직 좋아요한 글이 없어요');
    expect(render({ status: 'error', code: 401, message: '로그인이 필요합니다.' })).toContain('/login?next=/profile/me/likes');
    expect(render({ status: 'ready', value: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } })).toContain('아직 좋아요한 글이 없어요');
});
it('원본·안전한 본문·한국 시각을 표시하고 NULL 태그로 잘못된 프로필 링크를 만들지 않는다', () => {
    const author = { id: 3, nickname: '개발자', tag: null, profileImage: null };
    const html = render({ status: 'ready', value: { page: 0, size: 20, totalElements: 1, totalPages: 1, items: [{
        id: 12, type: 'REPOST', category: '개발', contentPreview: '<script>unsafe</script>', author,
        createdAt: '2026-11-01T03:00:00Z', likedAt: '2026-11-02T12:00:00+09:00',
        originalPost: { id: 10, contentPreview: '원본 내용', author },
    }] } });
    expect(html).toContain('&lt;script&gt;'); expect(html).not.toContain('<script>');
    expect(html).toContain('원본 내용'); expect(html).toContain('/feeds/12'); expect(html).not.toContain('/users/');
    expect(html).toContain('오후 12:00:00'); expect(html).toContain('aria-label="좋아요한 글 페이지"');
});
