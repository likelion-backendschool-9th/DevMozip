import { afterEach, describe, expect, it, vi } from 'vitest';
import { collaborationLabel, fetchProfileDetail, formatCareer, parseProfileDetail, parseOwnProfileDetail, profileImageUrl } from './profileDetail';
import { apiProfileFixture } from '@/tests/helpers/apiProfileFixture';
afterEach(() => vi.unstubAllGlobals());
describe('프로필 API 계약', () => {
    it.each([[null, '경력 미입력'], ['', '경력 미입력'], ['3년 차', '3년 차']])('경력 %s를 %s로 표시', (career, label) => {
        expect(formatCareer(career)).toBe(label);
    });
    it('알 수 없는 협업 상태를 협업 가능이나 불가로 단정하지 않는다', () => {
        expect(collaborationLabel('AVAILABLE')).toBe('협업 가능');
        expect(collaborationLabel('UNAVAILABLE')).toBe('현재 협업 쉬는 중');
        expect(collaborationLabel('PENDING')).toBe('협업 상태 미등록');
        expect(collaborationLabel(null)).toBe('협업 상태 미등록');
    });
    it('인증·결제 필드를 허용 목록에서 제거한다', () => {
        expect(parseProfileDetail({ ...apiProfileFixture, email: 'private', passwordHash: 'private', paymentMethods: [] })).toEqual(apiProfileFixture);
    });
    it.each([{ career: -1 }, { career: 18 }, { nickname: null }, { trustScore: Infinity }, { jobCategories: null }, { jobCategories: [1] }, { career: undefined }])('잘못된 백엔드 응답을 거부한다 %j', patch => {
        expect(() => parseProfileDetail({ ...apiProfileFixture, ...patch })).toThrow();
    });
    it.each(['javascript:alert(1)', 'data:image/svg+xml,test', '//external.test/a.png', '/\\external.test/a.png', 'https://user:password@example.com/a'])('안전하지 않은 이미지 %s를 사용하지 않는다', value => {
        expect(profileImageUrl(value)).toBeUndefined();
    });
    it('빈 이미지와 공개 이미지 경로를 구분한다', () => {
        expect(profileImageUrl(null)).toBeUndefined();
        expect(profileImageUrl('/avatar.png')).toBe('/avatar.png');
        expect(profileImageUrl('https://example.com/a.png')).toBe('https://example.com/a.png');
    });
    it('BFF만 호출하고 요청 취소 신호를 전달한다', async () => {
        const fetch = vi.fn().mockResolvedValue(Response.json(apiProfileFixture));
        vi.stubGlobal('fetch', fetch);
        const signal = new AbortController().signal;
        expect(await fetchProfileDetail('api-developer', signal)).toEqual(apiProfileFixture);
        expect(fetch).toHaveBeenCalledWith('/api/profiles/api-developer', { cache: 'no-store', signal });
    });
    it('404 상태를 UI에 전달한다', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('', { status: 404 })));
        await expect(fetchProfileDetail('missing', new AbortController().signal)).rejects.toMatchObject({ status: 404 });
    });
    it('다른 사용자의 응답으로 대체하지 않는다', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json(apiProfileFixture)));
        await expect(fetchProfileDetail('other', new AbortController().signal)).rejects.toThrow('태그 불일치');
    });
});


describe('최초 사용자 아이디 미설정', () => {
    it('본인 응답의 null 아이디만 허용하고 공개 응답은 계속 거절한다', () => {
        expect(parseOwnProfileDetail({ ...apiProfileFixture, tag: null, email: 'private' })).toEqual({ ...apiProfileFixture, tag: null });
        expect(() => parseProfileDetail({ ...apiProfileFixture, tag: null })).toThrow();
        for (const tag of [undefined, '', '../me', '한글']) expect(() => parseOwnProfileDetail({ ...apiProfileFixture, tag })).toThrow();
        expect(() => parseOwnProfileDetail({ ...apiProfileFixture, tag: null, id: -1 })).toThrow();
    });
});
