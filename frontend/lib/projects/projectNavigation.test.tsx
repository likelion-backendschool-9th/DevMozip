import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from '@/tests/helpers/RouterFixture';
import { ProjectBackLink } from '@/components/projects/ProjectBackLink';
import { projectDetailHref, projectReturnTarget, withProjectReturn } from './projectNavigation';

describe('프로젝트 진입 경로 복귀', () => {
    it('내 프로젝트의 탭·검색 조건을 상세 URL과 복귀 링크에 유지한다', () => {
        const source = '/my-projects?tab=HOSTED&query=React&page=2';
        const href = projectDetailHref(41, source);
        expect(new URL(href, 'http://localhost').searchParams.get('returnTo')).toBe(source);
        const html = renderToStaticMarkup(<MemoryRouter initialEntries={[href]}><ProjectBackLink /></MemoryRouter>);
        expect(html).toContain('href="/my-projects?tab=HOSTED&amp;query=React&amp;page=2"');
        expect(html).toContain('내 프로젝트');
    });
    it('직접 접속한 상세와 내 프로젝트 참여 화면은 안전한 기본 목록으로 이동한다', () => {
        expect(renderToStaticMarkup(<MemoryRouter initialEntries={['/projects/41']}><ProjectBackLink /></MemoryRouter>)).toContain('href="/projects"');
        expect(renderToStaticMarkup(<MemoryRouter initialEntries={['/my-projects/41/manage']}><ProjectBackLink /></MemoryRouter>)).toContain('href="/my-projects"');
    });
    it('프로필 활동에서 열면 활동 탭으로 돌아간다', () => {
        const source = '/users/jiho?tab=activity';
        const html = renderToStaticMarkup(<MemoryRouter initialEntries={[projectDetailHref(7,source)]}><ProjectBackLink /></MemoryRouter>);
        expect(html).toContain('href="/users/jiho?tab=activity"'); expect(html).toContain('프로필 활동');
        expect(withProjectReturn('/projects/7/manage', source)).toContain('returnTo=');
    });
    it.each(['https://example.com','//example.com','/\\example.com','/api/auth/logout','/projects/41','/my-projects/../api/health','/projects\n'])('외부/업무 처리 경로를 복귀 대상으로 허용하지 않는다: %s', value => {
        expect(projectReturnTarget(value)).toBe('/projects');
    });
});
