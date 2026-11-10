import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import LoginPage from '@/app/login/page';
import SignupPage from '@/app/signup/page';
import AccountSettingsPage from '@/app/(user)/settings/privacy/page';
import { AccountPasswordForm, passwordCopy } from '@/app/(user)/settings/privacy/_components/AccountPasswordForm';
import { resetLocalStores } from '@/lib/localStore';
import { syncServerSession } from '@/lib/state/authStore';
import { fixture } from '@/tests/helpers/RouterFixture';

const render = (url: string, element: React.ReactNode) => { fixture.url = url; return renderToStaticMarkup(element); };
beforeEach(() => resetLocalStores());
afterEach(() => { vi.unstubAllGlobals(); resetLocalStores(); });

describe('카카오 로그인 화면', () => {
    it('데모 안내 없이 카카오 시작 버튼을 보여 준다', () => {
        const html = render('/login', <LoginPage />);
        expect(html).toContain('카카오로 시작하기');
        expect(html).not.toContain('데모 안내');
    });
    it('URL의 결과 코드에 맞는 안내만 표시한다', () => {
        expect(render('/login?kakao=suspended', <LoginPage />)).toContain('이용이 정지된 계정입니다.');
        expect(render('/login?kakao=cancelled', <LoginPage />)).toContain('카카오 인증을 취소했습니다.');
        const injected = render(`/login?kakao=${encodeURIComponent('<b>가짜 안내</b>')}`, <LoginPage />);
        expect(injected).not.toContain('가짜 안내');
        expect(injected).not.toContain('role="alert"');
    });
});

describe('카카오 가입 화면', () => {
    it('이메일 가입은 비밀번호를 받고 카카오 가입 버튼을 보여 준다', () => {
        const html = render('/signup', <SignupPage />);
        expect(html).toContain('id="signup-password"');
        expect(html).toContain('id="signup-confirm"');
        expect(html).toContain('카카오로 가입하기');
    });
    it('카카오 인증을 마치고 돌아오면 비밀번호만 빼고 같은 항목을 받는다', () => {
        const html = render('/signup?kakao=signup', <SignupPage />);
        expect(html).not.toContain('id="signup-password"');
        expect(html).not.toContain('id="signup-confirm"');
        expect(html).not.toContain('카카오로 가입하기');
        expect(html).toContain('카카오 인증을 마쳤습니다.');
        for (const id of ['signup-email', 'signup-nickname', 'signup-job', 'signup-career']) expect(html).toContain(`id="${id}"`);
    });
    it('이미 등록된 카카오 계정이면 안내하고 이메일 가입 폼은 그대로 둔다', () => {
        const html = render('/signup?kakao=already-registered', <SignupPage />);
        expect(html).toContain('이미 등록된 회원입니다.');
        expect(html).toContain('id="signup-password"');
    });
});

describe('개인정보 설정 화면', () => {
    it('서버 상태를 받기 전에는 현재 비밀번호 입력을 보여 주지 않고 연동 버튼을 막는다', () => {
        syncServerSession({ identity: { username: 'member@devmozip.demo', role: 'USER' } });
        const html = render('/settings/privacy', <AccountSettingsPage />);
        expect(html).toContain('계정 정보를 불러오고 있어요');
        expect(html).not.toContain('현재 비밀번호');
        expect(html).toMatch(/<button[^>]*disabled[^>]*>카카오 연동/);
    });
    it('브라우저에 저장된 연동 표시를 쓰지 않는다', () => {
        const saved = JSON.stringify({ version: 1, accounts: { 'demo-user': { kakaoLinked: true, isPrivate: false } } });
        vi.stubGlobal('localStorage', { getItem: () => saved, setItem: () => {} });
        resetLocalStores();
        syncServerSession({ identity: { username: 'member@devmozip.demo', role: 'USER' } });
        const html = render('/settings/privacy', <AccountSettingsPage />);
        expect(html).toContain('연동되지 않음');
        expect(html).not.toContain('연동됨');
    });
    it('연동 결과 안내를 표시한다', () => {
        syncServerSession({ identity: { username: 'member@devmozip.demo', role: 'USER' } });
        expect(render('/settings/privacy?kakao=linked', <AccountSettingsPage />)).toContain('카카오 계정을 연동했습니다.');
        expect(render('/settings/privacy?kakao=unlink-blocked', <AccountSettingsPage />)).toContain('비밀번호를 설정했는지');
    });
    it('머리말에 체험 안내가 없다', () => {
        syncServerSession({ identity: { username: 'member@devmozip.demo', role: 'USER' } });
        const html = render('/settings/privacy', <AccountSettingsPage />);
        expect(html).toContain('실제 개인정보·결제 정보는 입력하지 마세요.');
        expect(html).not.toContain('체험');
    });
    it('예전에 저장된 데모 확인 시각을 표시하지 않는다', () => {
        const saved = JSON.stringify({ version: 1, accounts: { 'demo-user': { kakaoLinked: false, isPrivate: false, passwordConfirmedAt: '2026-01-01T00:00:00.000Z' } } });
        vi.stubGlobal('localStorage', { getItem: () => saved, setItem: () => {} });
        resetLocalStores();
        syncServerSession({ identity: { username: 'member@devmozip.demo', role: 'USER' } });
        expect(render('/settings/privacy', <AccountSettingsPage />)).not.toContain('최근 데모 확인');
    });
});

describe('비밀번호 폼', () => {
    const form = (firstPassword: boolean) => renderToStaticMarkup(
        <AccountPasswordForm
            firstPassword={firstPassword}
            value={{ currentPassword: '', newPassword: '', confirmPassword: '' }}
            error=""
            success=""
            busy={false}
            onChange={() => {}}
            onSubmit={() => {}}
        />,
    );
    it('비밀번호가 있는 회원은 현재 비밀번호를 입력하고 재설정 링크를 볼 수 있다', () => {
        const html = form(false);
        expect(html).toContain('현재 비밀번호');
        expect(html).toContain('새 비밀번호 확인');
        expect(html).toContain('href="/forgot-password"');
        expect(html).toContain('모든 기기에서 로그아웃');
        expect(html).toMatch(/<button[^>]*>비밀번호 변경<\/button>/);
    });
    it('비밀번호 없는 카카오 회원은 현재 비밀번호 없이 첫 비밀번호를 정한다', () => {
        const html = form(true);
        expect(html).not.toContain('현재 비밀번호');
        expect(html).not.toContain('forgot-password');
        expect(html).toContain('새 비밀번호 확인');
        expect(html).toMatch(/<button[^>]*>비밀번호 설정<\/button>/);
    });
    it('어떤 문구에도 데모 안내가 남아 있지 않다', () => {
        expect(JSON.stringify(passwordCopy)).not.toMatch(/데모|체험/);
        expect(form(false)).not.toMatch(/데모|체험/);
        expect(form(true)).not.toMatch(/데모|체험/);
    });
});
