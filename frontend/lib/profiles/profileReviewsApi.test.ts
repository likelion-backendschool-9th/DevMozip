import { expect, it } from 'vitest';
import { parseProfileReviews } from './profileReviewsApi';
const author = { id: 1, projectId: null, projectTitle: '비공개 프로젝트', authorUserId: 2, authorNickname: '동료', authorTag: null, rating: 5, keywords: ['소통'], submittedAt: '2026-11-10T10:00:00+09:00' };
const page = { userId: 1, count: 1, average: 5, distribution: [0, 0, 0, 0, 1], keywords: [{ name: '소통', count: 1 }], items: [author], page: 0, size: 20, totalPages: 1 };
it('공개 DTO만 남기고 비공개 프로젝트 링크가 없는 리뷰도 읽는다', () => {
    expect(parseProfileReviews({ ...page, tokenHash: 'discard' })).toEqual(page);
});
it('실제 0건 집계를 읽는다', () => {
    expect(parseProfileReviews({ ...page, count: 0, average: 0, distribution: [0, 0, 0, 0, 0], keywords: [], items: [], totalPages: 0 }).count).toBe(0);
});
it('분포·평균·키워드·페이지·별점이 잘못된 응답을 거절한다', () => {
    for (const patch of [{ distribution: [1, 0, 0, 0, 1] }, { average: 6 }, { totalPages: 2 }, { keywords: [{ name: '소통', count: 2 }] }, { items: [{ ...author, rating: 0 }] }, { items: [author, author] }])
        expect(() => parseProfileReviews({ ...page, ...patch })).toThrow();
});
