import { describe, expect, it } from 'vitest';
import { parseProfilePostsPage } from './profilePostsApi';
export const profilePostFixture = { id: 1, type: 'POST', title: '<script>제목</script>', category: 'INFO', contentPreview: '<b>소개</b>',
    author: { id: 1, nickname: '작성자', tag: 'writer', profileImage: null }, createdAt: '2026-11-10T10:00:00+09:00', originalPost: null };
export const profilePostsFixture = { items: [profilePostFixture], page: 0, size: 20, totalElements: 1, totalPages: 1 };
describe('프로필 포스팅 응답', () => {
    it('본문을 HTML로 해석하지 않고 공개 DTO만 유지한다', () => {
        expect(parseProfilePostsPage({ ...profilePostsFixture, secret: 'discard' })).toEqual(profilePostsFixture);
    });
    it('중복 ID·불명 타입·원본 누락·페이지 불일치를 거절한다', () => {
        for (const item of [{ ...profilePostFixture, id: 0 }, { ...profilePostFixture, type: 'UNKNOWN' }, { ...profilePostFixture, type: 'REPOST' }, { ...profilePostFixture, createdAt: 'invalid' }])
            expect(() => parseProfilePostsPage({ ...profilePostsFixture, items: [item] })).toThrow();
        expect(() => parseProfilePostsPage({ ...profilePostsFixture, items: [profilePostFixture, profilePostFixture], totalElements: 2 })).toThrow();
        expect(() => parseProfilePostsPage({ ...profilePostsFixture, totalPages: 2 })).toThrow();
    });
    it('퍼간 글의 원본 작성자와 미리보기를 보존한다', () => {
        const original = { id: 2, contentPreview: '원본', author: profilePostFixture.author };
        const value = parseProfilePostsPage({ ...profilePostsFixture, items: [{ ...profilePostFixture, type: 'REPOST', originalPost: original }] });
        expect(value.items[0].originalPost).toEqual(original);
    });
});
