import assert from 'node:assert/strict';
const { chromium } = await import(process.env.DEVMOZIP_PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.DEVMOZIP_BASE_URL || 'http://localhost:14174';
const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
try {
    const page = await browser.newPage();
    await page.goto(base);
    const start = () => page.getByRole('button', { name: '추천 시작', exact: true });
    const input = () => page.getByLabel('프로젝트 설명', { exact: true });
    const count = () => page.evaluate(() => JSON.parse(localStorage.getItem('devmozip:home:v1')).chat.conversations.guest.length);
    for (let index = 0; index < 3; index++) {
        await input().waitFor({ state: 'visible' });
        assert.equal(await input().inputValue(), '');
        await input().fill(`함께 만들 일정 관리 프로젝트 ${index}`);
        await start().waitFor({ state: 'visible' });
        assert.equal(await start().isEnabled(), true);
        // 실제 연속 submit 이벤트도 대화를 하나만 생성해야 한다.
        await input().evaluate(element => {
            element.form.requestSubmit();
            element.form.requestSubmit();
        });
        await page.waitForURL('**/ai/matching');
        assert.equal(await count(), index + 1);
        if (index === 1) await page.goBack();
        else await page.getByRole('link', { name: 'DevMozip 홈', exact: true }).first().click();
        await page.waitForURL(base + '/');
    }
    await input().waitFor({ state: 'visible' });
    assert.equal(await start().isEnabled(), true);
    console.log('PASS: 메인 → 대화 → 홈 링크/뒤로가기 3회, 재입력·버튼 활성화·중복 제출 차단');
} finally {
    await browser.close();
}
