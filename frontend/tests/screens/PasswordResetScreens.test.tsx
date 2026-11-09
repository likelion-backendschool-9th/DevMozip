import { beforeEach, describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import LoginPage from '@/app/login/page';
import PasswordRecoveryPage from '@/app/forgot-password/page';
import { PasswordResetForm } from '@/app/forgot-password/_components/PasswordResetForm';
import { PasswordResetRequestForm } from '@/app/forgot-password/_components/PasswordResetRequestForm';
import { resetLocalStores } from '@/lib/localStore';
import { fixture } from '@/tests/helpers/RouterFixture';

const render = (url: string, element: React.ReactNode) => { fixture.url = url; return renderToStaticMarkup(element); };
beforeEach(() => resetLocalStores());

describe('/forgot-password 화면', () => {
    it('토큰을 읽기 전에는 어떤 폼도 보여 주지 않는다', () => {
        const html = render('/forgot-password', <PasswordRecoveryPage />);
        expect(html).toContain('비밀번호를 잊으셨나요?');
        expect(html).not.toContain('<form');
    });
    it('요청 폼은 데모 안내 없이 이메일을 받는다', () => {
        const html = renderToStaticMarkup(<PasswordResetRequestForm />);
        expect(html).toContain('id="recovery-email"');
        expect(html).toContain('재설정 메일 받기');
        expect(html).not.toContain('데모');
    });
    it('새 비밀번호 폼은 비밀번호만 받고 토큰을 화면에 표시하지 않는다', () => {
        const html = renderToStaticMarkup(<PasswordResetForm token="secret-mail-token" onRestart={() => {}} />);
        expect(html).toContain('id="reset-password"');
        expect(html).toContain('id="reset-confirm"');
        expect(html).not.toContain('secret-mail-token');
    });
});

describe('로그인 화면의 비밀번호 결과 안내', () => {
    it('결과 코드에 맞는 안내만 표시한다', () => {
        expect(render('/login?password=reset', <LoginPage />)).toContain('비밀번호를 재설정했습니다.');
        expect(render('/login?password=changed', <LoginPage />)).toContain('비밀번호를 변경했습니다.');
        const injected = render(`/login?password=${encodeURIComponent('<b>가짜 안내</b>')}`, <LoginPage />);
        expect(injected).not.toContain('가짜 안내');
        expect(injected).not.toContain('role="status"');
    });
});
