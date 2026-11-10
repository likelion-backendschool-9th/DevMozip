import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { expect, it } from 'vitest';
import { ProfilePostsResult } from './ApiProfilePosts';
import type { ProfilePostsState } from './ApiProfilePosts';
const render = (result: ProfilePostsState) => renderToStaticMarkup(<ProfilePostsResult result={result} onPage={() => {}} onRetry={() => {}} />);
it('로딩·오류·실제 빈 목록을 구분한다', () => {
    expect(render({ status: 'loading' })).toContain('불러오는 중');
    const error = render({ status: 'error', message: '서버 오류' }); expect(error).toContain('다시 시도'); expect(error).not.toContain('포스팅 0개');
    expect(render({ status: 'ready', value: { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 } })).toContain('아직 표시할 포스팅이 없습니다.');
});
it('글 상세·원본 링크를 유지하고 본문 HTML을 이스케이프한다', () => {
    const author = { id: 1, nickname: '작성자', tag: 'writer', profileImage: null };
    const html = render({ status: 'ready', value: { items: [{ id: 1, type: 'REPOST', title: '<script>제목</script>', category: 'INFO', author, contentPreview: '<b>내용</b>', createdAt: null,
        originalPost: { id: 2, author, contentPreview: '원본' } }], page: 0, size: 20, totalElements: 1, totalPages: 1 } });
    expect(html).toContain('/feeds/1'); expect(html).toContain('/feeds/2'); expect(html).toContain('원본 작성자');
    expect(html).not.toContain('<script>'); expect(html).toContain('&lt;b&gt;내용&lt;/b&gt;');
});
