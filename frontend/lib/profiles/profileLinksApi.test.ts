import { afterEach, describe, expect, it, vi } from 'vitest';
import { parseProfileLinks, profileLinkUrl, fetchProfileLinks, saveProfileLinks } from './profileLinksApi';
import { renderToStaticMarkup } from 'react-dom/server';
import { createElement } from 'react';
import { ApiProfileLinksEditor } from '@/app/(user)/users/[userId]/_components/ApiProfileLinksEditor';
const empty = { github: '', blog: '', externalLinks: [] };
afterEach(() => vi.unstubAllGlobals());
describe('프로필 외부 링크', () => {
    it('필드를 제한하고 빈 목록·최대 개수·입력 공백 정리를 지원한다', () => {
        expect(parseProfileLinks({ ...empty, email: 'private' })).toEqual(empty);
        const links = Array.from({ length: 10 }, () => ({ label: ' 포트폴리오 ', url: ' https://example.com ' }));
        expect(parseProfileLinks({ ...empty, externalLinks: links }).externalLinks[0]).toEqual({ label: '포트폴리오', url: 'https://example.com' });
        expect(() => parseProfileLinks({ ...empty, externalLinks: [...links, links[0]] })).toThrow();
        for (const value of [{}, { ...empty, github: null }, { ...empty, externalLinks: null }, { ...empty, externalLinks: [{ label: '', url: 'https://example.com' }] }]) expect(() => parseProfileLinks(value)).toThrow();
    });
    it.each(['javascript:alert(1)', 'data:text/html,test', '//example.com', '/relative', 'https://u:p@example.com', 'https://example.com:99999', 'https://example.com/a b', 'https://example.com\\evil'])('위험하거나 모호한 URL %s를 거절한다', url => expect(() => profileLinkUrl(url)).toThrow());
    it('URL 500자와 이름 40자 경계를 검사한다', () => {
        expect(profileLinkUrl('https://example.com/' + 'a'.repeat(480))).toHaveLength(500);
        expect(() => profileLinkUrl('https://example.com/' + 'a'.repeat(481))).toThrow();
        expect(() => parseProfileLinks({ ...empty, externalLinks: [{ label: '가'.repeat(41), url: 'https://example.com' }] })).toThrow();
    });
    it('공개·본인 경로 구분, 취소 신호 및 명시적 삭제를 유지한다', async () => {
        const fetch = vi.fn().mockImplementation(async () => Response.json(empty)); vi.stubGlobal('fetch', fetch);
        const signal = new AbortController().signal;
        await fetchProfileLinks('public-user', false, signal);
        expect(fetch).toHaveBeenCalledWith('/api/profiles/public-user/links', { cache: 'no-store', signal });
        await fetchProfileLinks('public-user', true, signal);
        expect(fetch).toHaveBeenLastCalledWith('/api/profiles/me/links', { cache: 'no-store', signal });
        await saveProfileLinks(empty);
        expect(JSON.parse(fetch.mock.calls[2][1].body)).toEqual(empty);
    });
    it('잘못된 응답·저장 실패를 성공이나 빈 정보로 처리하지 않는다', async () => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ message: '다시 로그인' }, { status: 401 })));
        await expect(saveProfileLinks(empty)).rejects.toMatchObject({ status: 401 });
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(Response.json({ github: '', blog: '', externalLinks: null })));
        await expect(fetchProfileLinks('member', false)).rejects.toThrow();
    });
    it('기존 GitHub·블로그·추가 링크 수정 폼 배치를 유지한다', () => {
        const html = renderToStaticMarkup(createElement(ApiProfileLinksEditor, { initial: { ...empty, github: 'https://github.com/devmozip', externalLinks: [{ label: '포트폴리오', url: 'https://example.com' }] }, onClose: vi.fn(), onSaved: vi.fn() }));
        for (const text of ['GitHub URL', '블로그 URL', '추가 외부 링크', '외부 링크 추가', '추가 링크 1 삭제', '저장하기']) expect(html).toContain(text);
        expect(html).toContain('value="https://github.com/devmozip"');
        expect(html).toContain('value="포트폴리오"');
    });
});
