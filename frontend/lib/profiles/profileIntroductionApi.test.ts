import { afterEach, describe, expect, it, vi } from 'vitest';
import { parseIntroduction, fetchIntroduction, saveIntroduction } from './profileIntroductionApi';
afterEach(() => vi.unstubAllGlobals());
describe('상세 자기소개 통신', () => {
    it('미작성과 텍스트·HTML 형식을 구분하고 알 수 없는 필드는 제외한다', () => {
        expect(parseIntroduction({ introduction: null, format: 'text', email: 'private' })).toEqual({ introduction: null, format: 'text' });
        expect(parseIntroduction({ introduction: '<p>소개</p>', format: 'html' })).toEqual({ introduction: '<p>소개</p>', format: 'html' });
        expect(() => parseIntroduction({ introduction: '소개', format: null })).toThrow();
    });
    it('공개 태그와 본인 조회를 구분하고 취소 신호를 전달한다', async () => {
        const fetch = vi.fn().mockImplementation(async () => Response.json({ introduction: '소개', format: 'text' }));
        vi.stubGlobal('fetch', fetch);
        const signal = new AbortController().signal;
        await fetchIntroduction('public-tag', false, signal);
        expect(fetch).toHaveBeenCalledWith('/api/profiles/public-tag/introduction', { cache: 'no-store', signal });
        await fetchIntroduction('public-tag', true, signal);
        expect(fetch).toHaveBeenLastCalledWith('/api/profiles/me/introduction', { cache: 'no-store', signal });
    });
    it('빈 문자열 삭제를 PUT으로 전달한다', async () => {
        const fetch = vi.fn().mockResolvedValue(Response.json({ introduction: null, format: 'text' }));
        vi.stubGlobal('fetch', fetch);
        expect(await saveIntroduction('', 'text')).toEqual({ introduction: null, format: 'text' });
        expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ introduction: '', format: 'text' });
    });
    it('저장 실패를 성공으로 처리하지 않는다', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ message: '다시 로그인해 주세요.' }, { status: 401 })));
        await expect(saveIntroduction('내용', 'text')).rejects.toMatchObject({ status: 401, message: '다시 로그인해 주세요.' });
    });
});
