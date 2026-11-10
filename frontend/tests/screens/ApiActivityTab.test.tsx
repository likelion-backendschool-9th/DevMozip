import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
const mock = vi.hoisted(() => ({ resource: vi.fn(), auth: vi.fn() }));
vi.mock('@/lib/projects/useProjectApi', () => ({ useProjectResource: mock.resource }));
vi.mock('@/lib/state/authStore', () => ({ useAuth: mock.auth }));
import { ApiActivityTab } from '@/app/(user)/users/[userId]/_components/ApiActivityTab';
const activity = { projectId: 41, title: '프로젝트 활동 연결', imageUrl: 'https://example.test/project-cover.png', status: 'IN_PROGRESS', developmentStart: '2026-11-01', developmentEnd: '2026-12-01', completedAt: null, role: 'PARTICIPANT', jobRole: 'BACKEND', membershipStatus: 'LEFT' };
beforeEach(() => { mock.auth.mockReturnValue({ user: { email: 'viewer@example.test' } }); mock.resource.mockReturnValue({ data: null, error: null, reload: vi.fn() }); });
describe('실제 프로필 활동 탭', () => {
    it('프로젝트 제목 링크·직군·기간·참여 종료를 표시한다', () => {
        mock.resource.mockReturnValue({ data: { items: [activity], totalPages: 1 }, error: null });
        const html = renderToStaticMarkup(<ApiActivityTab slug="sample" own={false} />);
        expect(html).toContain('href="/projects/41?returnTo=%2Fusers%2Fsample%3Ftab%3Dactivity"');
        expect(html).toContain('src="https://example.test/project-cover.png"');
        expect(html).toContain('activity-mark'); expect(html).not.toContain('apiMark'); expect(html).toContain('프로젝트 활동 연결');
        expect(html).toContain('백엔드'); expect(html).toContain('참여 종료'); expect(html).toContain('2026-12-01');
        expect(html).not.toContain('내 프로젝트 관리');
    });
    it('본인은 me로 요청하고 관리 버튼을 표시한다', () => {
        const html = renderToStaticMarkup(<ApiActivityTab own />);
        expect(mock.resource).toHaveBeenCalledWith('/activity/me?page=0&size=10');
        expect(html).toContain('href="/my-projects"'); expect(html).toContain('불러오고 있습니다');
    });
    it('빈 목록과 페이지 이동을 구분한다', () => {
        mock.resource.mockReturnValue({ data: { items: [], totalPages: 0 }, error: null });
        expect(renderToStaticMarkup(<ApiActivityTab slug="sample" own={false} />)).toContain('표시할 DevMozip 프로젝트 참여 기록이 없습니다');
        mock.resource.mockReturnValue({ data: { items: [activity], totalPages: 2 }, error: null });
        expect(renderToStaticMarkup(<ApiActivityTab slug="sample" own={false} />)).toContain('프로젝트 활동 페이지');
    });
    it('통신 오류를 빈 이력으로 표시하지 않고 재시도한다', () => {
        mock.resource.mockReturnValue({ data: null, error: new Error('서버 연결 실패'), reload: vi.fn() });
        const html = renderToStaticMarkup(<ApiActivityTab slug="sample" own={false} />);
        expect(html).toContain('서버 연결 실패'); expect(html).toContain('다시 시도');
        expect(html).not.toContain('참여 기록이 없습니다');
    });
});

it('강퇴된 이력은 삭제하지 않고 강퇴됨으로 표시한다',()=>{
 mock.resource.mockReturnValue({data:{items:[{...activity,membershipStatus:'KICKED'}],totalPages:1},error:null});
 const html=renderToStaticMarkup(<ApiActivityTab slug="sample" own={false}/>);
 expect(html).toContain('강퇴됨');expect(html).toContain('프로젝트 활동 연결');
});
