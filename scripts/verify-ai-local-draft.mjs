import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

// Uses real server login. Run against an isolated local backend with the existing demo accounts.
const { chromium } = await import(process.env.DEVMOZIP_PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.DEVMOZIP_BASE_URL || 'http://localhost:14173';
const password = process.env.DEVMOZIP_VERIFY_PASSWORD;
if (!password) throw new Error('Set DEVMOZIP_VERIFY_PASSWORD to the local demo account password.');
const output = process.env.DEVMOZIP_VERIFY_OUTPUT || '/tmp/devmozip-ai-phase1-verification';
await mkdir(output, { recursive: true });
const report = { date: new Date().toISOString(), base, checks: [], aiRequests: [], pageErrors: [] };
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'ko-KR', timezoneId: 'Asia/Seoul' });
const page = await context.newPage();
page.setDefaultTimeout(15000);
page.on('request', request => {
    if (new URL(request.url()).pathname.startsWith('/api/v1/ai')) report.aiRequests.push(request.url());
});
page.on('pageerror', error => report.pageErrors.push(error.message));
const input = () => page.getByLabel('AI에게 메시지', { exact: true });
const home = () => page.evaluate(() => JSON.parse(localStorage.getItem('devmozip:home:v1')));
const active = (state, owner) => state.chat.conversations[owner]?.find(item => item.id === state.chat.active[owner]);
const go = async route => {
    await page.goto(base + route);
    await page.locator('main h1').first().waitFor();
};
const match = async () => {
    await page.getByRole('heading', { name: 'AI 팀원 추천', exact: true }).waitFor();
    await input().waitFor();
};
const login = async (email, value = password) => {
    await page.getByLabel('이메일', { exact: true }).fill(email);
    await page.getByLabel('비밀번호', { exact: true }).fill(value);
    await page.getByRole('button', { name: '로그인', exact: true }).click();
};
const loginFromMatching = async email => {
    await go('/login?next=%2Fai%2Fmatching');
    await login(email);
    await page.waitForURL(url => url.pathname === '/ai/matching');
    await page.getByRole('button', { name: '로그아웃', exact: true }).waitFor();
    await match();
};
const logout = async () => {
    // The header updates optimistically; wait for the server action to clear HttpOnly cookies.
    const [response] = await Promise.all([
        page.waitForResponse(response => response.request().method() === 'POST' && !!response.request().headers()['next-action']),
        page.getByRole('button', { name: '로그아웃', exact: true }).click(),
    ]);
    await response.finished();
    assert(response.ok());
    assert(!(await context.cookies()).some(cookie => ['devmozip_access', 'devmozip_refresh'].includes(cookie.name)));
    await page.getByRole('button', { name: '로그아웃', exact: true }).waitFor({ state: 'hidden' });
};
const check = async (name, callback) => {
    try {
        await callback();
        report.checks.push({ name, status: 'passed' });
        console.log('PASS ' + name);
    } catch (error) {
        report.checks.push({ name, status: 'failed', error: error.message });
        await page.screenshot({ path: path.join(output, 'failure.png'), fullPage: true });
        throw error;
    }
};

let guest, usage, aId;
const draft = '아직 전송하지 않은 입력문입니다.';
try {
    await check('비로그인 메시지·선택 조건·미전송 입력 보존', async () => {
        await go('/ai/matching');
        await match();
        await input().fill('웹 프로젝트 팀원을 찾고 있습니다.');
        await page.getByRole('button', { name: '메시지 전송', exact: true }).click();
        await page.getByRole('log').getByText('웹 프로젝트 팀원을 찾고 있습니다.', { exact: true }).waitFor();
        await page.locator('.chat-conditions summary').click();
        await page.getByLabel('프로젝트 설명', { exact: true }).fill('협업 일정 관리 웹 서비스를 개발합니다.');
        for (const name of ['웹', '생산성', 'React', '백엔드']) {
            await page.getByRole('button', { name, exact: true }).click();
        }
        await page.getByLabel('백엔드 모집 인원').fill('2');
        await page.getByLabel('요구 경력', { exact: true }).selectOption('경력 무관');
        await page.getByLabel('예상 프로젝트 기간', { exact: true }).selectOption('3개월');
        await input().fill(draft);
        const state = await home();
        guest = active(state, 'guest');
        usage = state.matching.usage;
        assert.equal(guest.draft, draft);
        assert.deepEqual(guest.messages.map(message => message.role), ['user']);
        assert.deepEqual(guest.requirements.roles, [{ name: '백엔드', count: 2 }]);
        await page.reload();
        await match();
        assert.equal(await input().inputValue(), draft);
        assert.deepEqual(active(await home(), 'guest'), guest);
    });

    await check('로그인 취소·실패 후 guest 원본 유지', async () => {
        await page.getByRole('link', { name: '로그인하고 초안 이어가기', exact: true }).click();
        await page.waitForURL(url => url.pathname === '/login');
        await page.goBack();
        await match();
        assert.deepEqual(active(await home(), 'guest'), guest);
        await page.getByRole('link', { name: '로그인하고 초안 이어가기', exact: true }).click();
        await login('member@devmozip.demo', 'wrong-password');
        await page.getByRole('alert').waitFor();
        assert.equal(new URL(page.url()).pathname, '/login');
        await go('/ai/matching');
        await match();
        assert.equal(await input().inputValue(), draft);
        assert.deepEqual(active(await home(), 'guest'), guest);
    });

    await check('실제 로그인·HttpOnly 쿠키·자동 인계 없음', async () => {
        await loginFromMatching('member@devmozip.demo');
        await page.getByRole('button', { name: '로그인 전 초안 이어받기', exact: true }).waitFor();
        const state = await home();
        assert.deepEqual(active(state, 'guest'), guest);
        assert.equal(state.chat.conversations['demo-user']?.length || 0, 0);
        const cookies = await context.cookies();
        assert(cookies.some(cookie => cookie.name === 'devmozip_access' && cookie.httpOnly));
        assert(!(await page.evaluate(() => document.cookie)).includes('devmozip_access'));
    });

    await check('인계 저장 실패 시 원본 유지·재시도', async () => {
        await page.evaluate(() => {
            window.restoreDraftStorage = Storage.prototype.setItem;
            Storage.prototype.setItem = function (key, value) {
                if (key === 'devmozip:home:v1') throw new DOMException('Verification quota failure', 'QuotaExceededError');
                return window.restoreDraftStorage.call(this, key, value);
            };
        });
        await page.getByRole('button', { name: '로그인 전 초안 이어받기', exact: true }).click();
        await page.getByRole('alert').filter({ hasText: '비로그인 원본을 유지했습니다.' }).waitFor();
        assert.deepEqual(active(await home(), 'guest'), guest);
        await page.evaluate(() => { Storage.prototype.setItem = window.restoreDraftStorage; });
        await page.getByRole('button', { name: '로그인 전 초안 이어받기', exact: true }).click();
        await page.getByRole('button', { name: '로그인 전 초안 이어받기', exact: true }).waitFor({ state: 'hidden' });
        const state = await home();
        const moved = active(state, 'demo-user');
        aId = moved.id;
        assert.deepEqual(moved, guest);
        assert(!state.chat.conversations.guest.some(item => item.id === guest.id));
        await page.reload();
        await match();
        assert.equal(await input().inputValue(), draft);
        assert.deepEqual(active(await home(), 'demo-user'), guest);
        await page.screenshot({ path: path.join(output, 'resumed-draft-1440.png'), fullPage: true });
    });

    await check('로그인 추천 버튼은 준비 안내·추천/자동 답변/차감 없음', async () => {
        await page.locator('.chat-conditions summary').click();
        await page.getByRole('button', { name: '조건으로 추천 받기', exact: true }).click();
        await page.getByRole('alert').filter({ hasText: '추천 실행은 아직 준비 중입니다.' }).waitFor();
        const state = await home();
        assert.deepEqual(state.matching.usage, usage);
        assert.deepEqual(active(state, 'demo-user').messages.map(message => message.role), ['user']);
        assert.deepEqual(report.aiRequests, []);
    });

    await check('로그아웃·다른 실계정 로그인 후 초안 분리', async () => {
        await logout();
        await go('/ai/matching');
        await match();
        assert.equal(await input().inputValue(), '');
        assert.equal(await page.getByRole('log').getByText('웹 프로젝트 팀원을 찾고 있습니다.', { exact: true }).count(), 0);
        await loginFromMatching('premium@devmozip.demo');
        assert.equal(await input().inputValue(), '');
        assert.equal(await page.getByRole('log').getByText('웹 프로젝트 팀원을 찾고 있습니다.', { exact: true }).count(), 0);
        await input().fill('프리미엄 계정만의 별도 초안');
        assert.equal(active(await home(), 'demo-premium').draft, '프리미엄 계정만의 별도 초안');
        assert.equal(active(await home(), 'demo-user').id, aId);
        await logout();
        await loginFromMatching('member@devmozip.demo');
        assert.equal(await input().inputValue(), draft);
        assert.deepEqual(active(await home(), 'demo-user'), guest);
        assert.deepEqual((await home()).matching.usage, usage);
        assert.deepEqual(report.aiRequests, []);
        assert.deepEqual(report.pageErrors, []);
    });
} finally {
    await writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
    await browser.close();
}
