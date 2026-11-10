import { afterEach, describe, expect, it, vi } from 'vitest';
import { parseProfileOptions, fetchProfileOptions, saveProfileOptions } from './profileOptionsApi';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { ProfileTagEditor } from '@/components/profile/ProfileTagEditor';
afterEach(() => vi.unstubAllGlobals());
describe('프로필 기술 스택·관심 분야', () => {
    it('공개 필드만 반환하고 미등록과 오류를 구분한다', () => {
        expect(parseProfileOptions({ skills: [], interests: [], email: 'private' })).toEqual({ skills: [], interests: [] });
        for (const body of [{}, { skills: null, interests: [] }, { skills: [''], interests: [] }, { skills: [], interests: [3] }]) expect(() => parseProfileOptions(body)).toThrow();
    });
    it('공개·본인 조회를 구분하고 취소 신호를 전달한다', async () => {
        const fetch = vi.fn().mockImplementation(async () => Response.json({ skills: ['Java'], interests: ['AI'] }));
        vi.stubGlobal('fetch', fetch);
        const signal = new AbortController().signal;
        await fetchProfileOptions('public-user', false, signal);
        expect(fetch).toHaveBeenCalledWith('/api/profiles/public-user/options', { cache: 'no-store', signal });
        await fetchProfileOptions('public-user', true, signal);
        expect(fetch).toHaveBeenLastCalledWith('/api/profiles/me/options', { cache: 'no-store', signal });
    });
    it('빈 목록 저장을 유지하고 사용자 정보는 제외한다', async () => {
        const fetch = vi.fn().mockResolvedValue(Response.json({ skills: [], interests: [] }));
        vi.stubGlobal('fetch', fetch);
        await saveProfileOptions({ skills: [], interests: [], id: 2 } as never);
        expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({ skills: [], interests: [] });
    });
    it('인증 실패를 성공으로 처리하지 않는다', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ message: '다시 로그인' }, { status: 401 })));
        await expect(saveProfileOptions({ skills: [], interests: [] })).rejects.toMatchObject({ status: 401 });
    });
    it('API 선택기는 직접 입력을 숨기고 프로토타입은 유지한다', () => {
        const props = { label: '기술 스택', options: ['Java'], value: ['Java'], onChange: vi.fn() };
        const html = renderToStaticMarkup(createElement(ProfileTagEditor, { ...props, allowCustom: false }));
        expect(html).toContain('aria-pressed="true"'); expect(html).not.toContain('직접 입력');
        expect(renderToStaticMarkup(createElement(ProfileTagEditor, props))).toContain('직접 입력');
    });
});
