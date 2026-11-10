import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { expect, it } from 'vitest';
import { ProfileReviewsResult } from './ApiProfileReviews';
import type { ProfileReviewsState } from './ApiProfileReviews';
const render = (result: ProfileReviewsState) => renderToStaticMarkup(<ProfileReviewsResult result={result} trustScore={38.5} onPage={() => {}} onRetry={() => {}} />);
it('로딩·오류·리뷰 없는 집계를 구분하고 저장된 온도를 사용한다', () => {
    expect(render({ status: 'loading' })).toContain('받은 리뷰를 불러오는 중');
    expect(render({ status: 'error', message: '오류' })).toContain('다시 시도');
    const html = render({ status: 'ready', value: { userId: 1, count: 0, average: 0, distribution: [0, 0, 0, 0, 0], keywords: [], items: [], page: 0, size: 20, totalPages: 0 } });
    expect(html).toContain('아직 받은 리뷰가 없습니다.');expect(html).toContain('38.5');expect(html).not.toContain('데모');
});
it('별점·키워드·작성자를 표시하고 비공개 프로젝트의 링크를 만들지 않는다', () => {
    const html = render({ status: 'ready', value: { userId: 1, count: 1, average: 5, distribution: [0, 0, 0, 0, 1], keywords: [{ name: '소통', count: 1 }], items: [{ id: 1, projectId: null, projectTitle: '비공개 프로젝트', authorUserId: 2,
        authorNickname: '<script>작성자</script>', authorTag: 'writer', rating: 5, keywords: ['소통'], submittedAt: null }], page: 0, size: 20, totalPages: 1 } });
    expect(html).toContain('별점 5점');expect(html).toContain('소통');expect(html).toContain('/users/writer');expect(html).not.toContain('/projects/');expect(html).not.toContain('<script>');
});
