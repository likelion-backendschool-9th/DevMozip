import { beforeEach, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from '@/tests/helpers/RouterFixture';
import type { ProjectListRecord } from '@/types/projectApi';
const state = vi.hoisted(() => ({ user: { email: 'liked@example.test' } as {email: string} | null, likes: vi.fn() }));
vi.mock('@/lib/state/authStore', () => ({ useAuth: () => ({ user: state.user }) }));
vi.mock('@/lib/projects/projectLikeStore', () => ({ useProjectLikes: state.likes }));
vi.mock('@/components/projects/ProjectSummaryCard', () => ({ ProjectSummaryCard: ({project}: {project: ProjectListRecord}) => <a href={`/projects/${project.id}`}>{project.title}</a> }));
import LikesPage from '../page';
import { LikedProjectsResult } from './LikedProjectsResult';
const ready = { ready: true, loading: false, pending: false, items: [] as ProjectListRecord[], error: null, reload: vi.fn(), toggle: vi.fn() };
const render = (query = '') => renderToStaticMarkup(<MemoryRouter initialEntries={['/profile/me/likes'+query]}><LikesPage /></MemoryRouter>);
beforeEach(() => { state.user = { email: 'liked@example.test' }; state.likes.mockReturnValue(ready); });
it('기본 포스트 탭과 프로젝트 탭을 제공하고 기존 포스트 목록을 유지한다', () => {
    const html = render();
    expect(html).toContain('id="liked-posts-tab" role="tab" aria-selected="true"');
    expect(html).toContain('id="liked-projects-tab" role="tab" aria-selected="false"');
    expect(html).toContain('좋아요한 글을 불러오는 중');
    expect(state.likes).not.toHaveBeenCalled();
});
it('프로젝트 탭은 포스트 페이지 조건과 무관하게 프로젝트 목록을 표시한다', () => {
    const html = render('?tab=projects&page=invalid');
    expect(html).toContain('id="liked-projects-tab" role="tab" aria-selected="true"');
    expect(html).toContain('아직 좋아요한 프로젝트가 없어요.');
    expect(html).toContain('href="/projects"');
    expect(html).not.toContain('페이지와 조회 개수를 확인');
});
it('비로그인 상태에서는 프로젝트 API를 요청하지 않고 로그인 안내를 표시한다', () => {
    state.user = null;
    expect(render('?tab=projects')).toContain('로그인하고 내 목록을 확인');
    expect(state.likes).not.toHaveBeenCalled();
});
it('프로젝트 로딩과 오류를 빈 목록으로 표시하지 않는다', () => {
    state.likes.mockReturnValue({...ready, ready: false, loading: true});
    expect(renderToStaticMarkup(<LikedProjectsResult />)).toContain('불러오는 중');
    state.likes.mockReturnValue({...ready, error: {message: '목록 조회 실패', status: 502}});
    const html = renderToStaticMarkup(<LikedProjectsResult />);
    expect(html).toContain('목록 조회 실패'); expect(html).toContain('다시 조회');
    expect(html).not.toContain('아직 좋아요한');
});
it('프로젝트 카드와 취소 버튼을 제공하고 처리 중에는 중복 취소를 막는다', () => {
    state.likes.mockReturnValue({...ready, pending: true, items: [{id: 7, title: '팀 프로젝트'}]});
    const html = renderToStaticMarkup(<LikedProjectsResult />);
    expect(html).toContain('href="/projects/7"');
    expect(html).toContain('팀 프로젝트 좋아요 취소');
    expect(html).toContain('disabled=""');
    expect(html).toContain('좋아요한 프로젝트 1개');
});
