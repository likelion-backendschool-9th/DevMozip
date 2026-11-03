import { afterEach, describe, expect, it, vi } from 'vitest';
import { collaborationLabel, fetchProfileDetail, formatCareer, parseProfileDetail, profileImageUrl } from './profileDetail';
import { apiProfileFixture } from '@/tests/helpers/apiProfileFixture';
afterEach(() => vi.unstubAllGlobals());
describe('프로필 API 계약', () => {
    it.each([[null, '경력 미입력'], [0, '신입'], [1, '1개월'], [12, '1년'], [18, '1년 6개월'], [120, '10년']])('경력 %s를 %s로 표시', (months, label) => {
        expect(formatCareer(months as number | null)).toBe(label);
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
    it.each([{ career: -1 }, { career: 1.5 }, { nickname: null }, { trustScore: Infinity }, { jobCategory: null }, { career: undefined }])('잘못된 백엔드 응답을 거부한다 %j', patch => {
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
