import { afterEach, describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { AccountLayout } from '@/app/(user)/_components/AccountLayout';
import { ApiProfileEditor } from '@/app/(user)/users/[userId]/_components/ApiProfileEditor';
import { ProfileApiResult } from '@/app/(user)/users/[userId]/_components/ApiProfileContent';
import ProfilePage from '@/app/(user)/users/[userId]/page';
import { apiProfileFixture } from '@/tests/helpers/apiProfileFixture';
import { resetLocalStores } from '@/lib/localStore';
import { syncServerSession } from '@/lib/state/authStore';
import { MemoryRouter, Route, Routes } from '@/tests/helpers/RouterFixture';
const render = (element: React.ReactNode) => renderToStaticMarkup(element);
afterEach(() => resetLocalStores());
describe('API 프로필 화면', () => {
    it('서버 프로필에서도 원래 사이드바 메뉴를 유지한다', () => {
        resetLocalStores(); syncServerSession({ identity: { username: 'member@profile.test', role: 'USER' } });
        const html = render(<MemoryRouter initialEntries={['/users/api-developer']}><Routes><Route path="/users/:userId" element={<AccountLayout><span>본문</span></AccountLayout>} /></Routes></MemoryRouter>);
        for (const label of ['MY WORKSPACE', '내 프로필', '좋아요한 글', '내 북마크함', '개인정보 및 계정 설정', '본문']) expect(html).toContain(label);
        expect(html).toContain('href="/profile/me"');
    });
    it('기존 수정 창 배치에 서버 값과 경력 문구를 채운다', () => {
        const html = render(<ApiProfileEditor profile={{ ...apiProfileFixture, career: '2년 차', contactHours: '평일 저녁' }} onClose={() => {}} onSaved={() => {}} />);
        for (const label of ['기본 정보 수정', '배너 이미지', '프로필 이미지', '닉네임', '태그', '한 줄 소개', '직군', '경력', '연락 가능 시간', '새로운 프로젝트에 협업할 수 있어요.']) expect(html).toContain(label);
        expect(html).toMatch(/<option[^>]*selected[^>]*>2년 차</);
        expect(html).toContain('value="평일 저녁"');
        expect(html).toContain('변경·삭제 즉시 저장');
    });
    it('실제 응답의 경력·온도를 표시하고 기존 카드·탭 배치를 유지하고 Mock 수치를 섞지 않는다', () => {
        const html = render(<ProfileApiResult result={{ status: 'ready', profile: apiProfileFixture }} onRetry={() => {}} />);
        for (const text of ['API 개발자', '@api-developer', '2년 차', '38.5', '서버에서 가져온 소개', '협업 가능']) expect(html).toContain(text);
        for (const text of ['36.5', 'PRO', '이서준']) expect(html).not.toContain(text);
        expect(html).toContain('<svg');
        expect(html).toContain('프로필 수정');
        expect(html).toContain('가입일 · 조회 연결 전');
        expect(html).toContain('팔로워');
        expect((html.match(/role="tab"/g) || [])).toHaveLength(5);
        expect(html).toContain('ABOUT ME');
        expect(html).toContain('기술 스택');
        expect(html).toContain('외부 링크');
        expect(html).toContain('disabled');
    });
    it.each(['about', 'portfolio', 'posts', 'activity', 'reviews'])('서버 모드에서도 %s 탭의 기존 틀을 유지한다', tab => {
        const html = render(<MemoryRouter initialEntries={[`/users/api-developer?tab=${tab}`]}><ProfileApiResult result={{ status: 'ready', profile: apiProfileFixture }} onRetry={() => {}} /></MemoryRouter>);
        expect(html).toContain(`id="profile-panel-${tab}"`);
        expect((html.match(/role="tab"/g) || [])).toHaveLength(5);
        if (tab === 'about') {
            expect(html).toContain('외부 링크를 불러오는 중…');
            expect(html).not.toContain('외부 링크는 아직 조회 API에 연결되지 않았습니다.');
        } else if (tab === 'portfolio') expect(html).toContain('포트폴리오를 불러오는 중');
        else if (tab === 'activity') { expect(html).toContain('프로젝트 이력을 불러오고 있습니다.'); expect(html).not.toContain('활동 조회 연결 전'); }
        else expect(html).toContain('아직 조회 API에 연결되지 않았습니다.');
        expect(html).toContain('38.5');
    });
    it('미입력 정보는 빈 값을 명확히 표시한다', () => {
        const html = render(<ProfileApiResult result={{ status: 'ready', profile: { ...apiProfileFixture, career: null, summary: null, collaborationStatus: null } }} onRetry={() => {}} />);
        expect(html).toContain('경력 미입력');
        expect(html).toContain('한 줄 소개가 없습니다.');
        expect(html).toContain('협업 상태 미등록');
    });
    it('로딩 중에 이전 Mock 프로필을 표시하지 않는다', () => {
        const html = render(<ProfileApiResult result={{ status: 'loading' }} onRetry={() => {}} />);
        expect(html).toContain('role="status"');
        expect(html).not.toContain('이서준');
    });
    it.each([401, 403, 404, 502])('오류 %s를 명확히 표시한다', code => {
        const html = render(<ProfileApiResult result={{ status: 'error', code }} onRetry={() => {}} />);
        expect(html).toContain('role="alert"');
        expect(html.includes('다시 시도</button>')).toBe(code !== 404);
    });
    it('서버 모드는 Mock에 없는 태그도 조회 화면으로 진입한다', () => {
        resetLocalStores(); syncServerSession({ identity: null });
        const html = render(<MemoryRouter initialEntries={['/users/api-developer']}><Routes><Route path="/users/:userId" element={<ProfilePage />} /></Routes></MemoryRouter>);
        expect(html).toContain('프로필 정보를 불러오고 있어요');
        expect(html).not.toContain('초기화된 Mock');
    });
});


it('사용자 아이디 미설정 본인도 프로필과 편집 창을 열 수 있다', () => {
    const profile = { ...apiProfileFixture, tag: null };
    const html = render(<ProfileApiResult result={{ status: 'ready', profile }} onRetry={() => {}} onEdit={() => {}} />);
    expect(html).toContain('사용자 아이디 미설정'); expect(html).toContain('프로필 수정');
    expect(html).toContain('자기소개를 불러오고 있어요'); expect(html).toContain('외부 링크를 불러오는 중');
    expect(html).not.toContain('@null'); expect(html).not.toContain('/users/null');
    const form = render(<ApiProfileEditor profile={profile} onClose={() => {}} onSaved={() => {}} />);
    expect(form).toContain('사용자 아이디를 설정하면 공개 프로필 주소가 만들어져요.');
    expect(form).not.toContain('value="null"');
});
