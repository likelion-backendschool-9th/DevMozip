import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import AccountSettingsPage from '@/app/(user)/settings/privacy/page';
import { AccountLockPanel, AccountLockView, MockAccountLock, ServerAccountLock } from '@/app/(user)/settings/privacy/_components/AccountLockPanel';
import { resetLocalStores } from '@/lib/localStore';
import { syncServerSession } from '@/lib/state/authStore';
import { fixture } from '@/tests/helpers/RouterFixture';

const noop = () => {};
const view = (props: Partial<Parameters<typeof AccountLockView>[0]>) =>
    renderToStaticMarkup(<AccountLockView state={{ status: 'loading' }} busy={false} error="" onRequest={noop} {...props} />);
// 데모 계정 member@devmozip.demo의 Mock 계정 ID는 demo-user다.
const storeMockPrivacy = (isPrivate: boolean) => {
    const saved = JSON.stringify({ version: 1, accounts: { 'demo-user': { kakaoLinked: false, isPrivate } } });
    vi.stubGlobal('localStorage', { getItem: () => saved, setItem: () => {} });
    resetLocalStores();
};
beforeEach(() => resetLocalStores());
afterEach(() => { vi.unstubAllGlobals(); resetLocalStores(); });

describe('계정 공개 영역 표시', () => {
    it('불러오는 동안에는 전환 버튼을 보여 주지 않는다', () => {
        const html = view({ state: { status: 'loading' } });
        expect(html).toContain('공개 설정을 불러오고 있어요');
        expect(html).not.toContain('전환');
    });
    it('불러오지 못하면 안내와 다시 시도 버튼을 보여 준다', () => {
        const html = view({ state: { status: 'error', message: '공개 설정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.' }, onRetry: noop });
        expect(html).toContain('role="alert"');
        expect(html).toContain('공개 설정을 불러오지 못했습니다.');
        expect(html).toContain('다시 시도');
        expect(html).not.toContain('전환');
    });
    it('현재 값에 맞는 문구와 전환 버튼을 보여 준다', () => {
        const publicHtml = view({ state: { status: 'ready', isPrivate: false } });
        expect(publicHtml).toContain('<strong>공개 계정</strong>');
        expect(publicHtml).toContain('다른 사용자가 내 프로필과 SNS 게시글을 볼 수 있습니다.');
        expect(publicHtml).toContain('비공개 계정으로 전환');
        const privateHtml = view({ state: { status: 'ready', isPrivate: true } });
        expect(privateHtml).toContain('<strong>비공개 계정</strong>');
        expect(privateHtml).toContain('본인은 모든 기능을 그대로 이용할 수 있습니다.');
        expect(privateHtml).toContain('공개 계정으로 전환');
    });
    it('요청 중에는 버튼을 막고, 실패하면 이전 값과 안내를 함께 보여 준다', () => {
        expect(view({ state: { status: 'ready', isPrivate: false }, busy: true })).toMatch(/<button[^>]*disabled[^>]*>비공개 계정으로 전환/);
        const failed = view({ state: { status: 'ready', isPrivate: false }, error: '공개 설정을 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.' });
        expect(failed).toContain('<strong>공개 계정</strong>');
        expect(failed).toContain('공개 설정을 바꾸지 못했습니다.');
    });
});

describe('Mock 토글과 계정 공개 영역 (D4 B)', () => {
    it('토글 ON은 기존 Mock 저장값을 바로 보여 준다', () => {
        storeMockPrivacy(true);
        syncServerSession({ identity: { username: 'member@devmozip.demo', role: 'USER' } });
        expect(renderToStaticMarkup(<MockAccountLock />)).toContain('<strong>비공개 계정</strong>');
        // 서버 렌더링은 토글 ON으로 시작하므로 Mock 값을 쓴다.
        expect(renderToStaticMarkup(<AccountLockPanel />)).toContain('<strong>비공개 계정</strong>');
    });
    it('토글 OFF는 Mock 저장값을 쓰지 않고 서버 값을 불러온다', () => {
        storeMockPrivacy(true);
        syncServerSession({ identity: { username: 'member@devmozip.demo', role: 'USER' } });
        const html = renderToStaticMarkup(<ServerAccountLock />);
        expect(html).toContain('공개 설정을 불러오고 있어요');
        expect(html).not.toContain('비공개 계정');
    });
});

describe('개인정보 설정 페이지 구성', () => {
    it('이메일은 로그인 계정 안내를 보여 주고 네 영역을 함께 둔다', () => {
        syncServerSession({ identity: { username: 'member@devmozip.demo', role: 'USER' } });
        fixture.url = '/settings/privacy';
        const html = renderToStaticMarkup(<AccountSettingsPage />);
        expect(html).toContain('value="member@devmozip.demo"');
        expect(html).toContain('로그인한 계정의 이메일입니다.');
        expect(html).not.toContain('Mock 계정 정보');
        for (const heading of ['계정 정보', '카카오 계정', '결제 정보', '계정 공개 설정']) expect(html).toContain(heading);
    });
});
