import { afterEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AdminProjectsView } from '@/app/admin/projects/_components/ApiAdminProjects';
import AdminProjectsPage from '@/app/admin/projects/page';
import type { AdminProject } from '@/types/adminApi';

const noop = () => {};
const project: AdminProject = { id: 7, title: '공고', hostTag: 'host', hostNickname: '주최자', status: 'RECRUITING', isPublic: true, restricted: false,
    recruitmentEnd: '2026-11-30', developmentEnd: '2027-01-31', createdAt: '2026-11-10T10:00:00' };
const view = (items: AdminProject[]) => renderToStaticMarkup(<AdminProjectsView load={{ status: 'ready', data: { items, totalElements: items.length, page: 0, totalPages: 1 } }}
    busy={false} onRetry={noop} onPage={noop} onRestrict={noop} onDelete={noop} />);
afterEach(() => vi.unstubAllGlobals());

describe('서버 프로젝트 공고 관리 화면 (14.4·14.5)', () => {
    it('모집 중인 공고만 삭제 버튼을 보여 준다', () => {
        const html = view([project, { ...project, id: 8, status: 'IN_PROGRESS', restricted: true, isPublic: false, hostNickname: null }]);
        expect(html).toContain('href="/projects/7"');
        expect(html).toContain('주최자 · 모집 중 · 공개 · 모집 마감 2026-11-30');
        expect(html).toContain('탈퇴한 회원 · 프로젝트 진행 중 · 비공개');
        expect(html).toContain('관리자 숨김');
        expect(html).toContain('숨김 해제');
        expect(html.match(/>삭제</g)?.length).toBe(1);
    });
    it('빈 목록을 안내한다', () => {
        expect(view([])).toContain('조건에 맞는 공고가 없습니다.');
    });
    it('Mock 토글 ON(서버 렌더링 기본값)에서는 안내만 보여 준다', () => {
        const html = renderToStaticMarkup(<AdminProjectsPage />);
        expect(html).toContain('Mock 토글을 끄면');
        expect(html).not.toContain('<form');
    });
});
