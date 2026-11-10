import { afterEach, expect, it, vi } from 'vitest';
import { clearWithdrawalLocalState } from './withdrawalLocalState';
import { HOME_KEY } from '@/lib/matching/homeService';

afterEach(() => vi.unstubAllGlobals());

it('탈퇴 계정의 초안만 두 작업 공간에서 제거하고 다른 계정은 유지한다', () => {
    const data = new Map<string, string>();
    vi.stubGlobal('localStorage', { getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => data.set(key, value) });
    vi.stubGlobal('sessionStorage', { removeItem: vi.fn() });
    const owner = 'account:withdraw@example.test';
    const state = { version: 1, inputOwner: owner, input: { description: '삭제할 초안' }, chat: { conversations: { [owner]: ['삭제'], other: ['유지'] }, active: { [owner]: 'mine', other: 'other' } }, matching: { usage: { [owner]: {}, other: {} }, last: { [owner]: {}, other: {} } } };
    const keys = [HOME_KEY, `devmozip:empty-mode:v1:${HOME_KEY}`];
    keys.forEach(key => data.set(key, JSON.stringify(state)));
    expect(clearWithdrawalLocalState({ id: 'actual-user', email: 'Withdraw@example.test' })).toBe(true);
    for (const key of keys) {
        const saved = JSON.parse(data.get(key)!);
        expect(saved.chat.conversations[owner]).toBeUndefined();
        expect(saved.chat.conversations.other).toEqual(['유지']);
        expect(saved.matching.last[owner]).toBeUndefined();
        expect(saved.inputOwner).toBeUndefined();
    }
});

it('브라우저 저장소 삭제 실패를 호출자에게 알린다', () => {
    vi.stubGlobal('localStorage', { getItem: () => { throw new Error('blocked'); } });
    expect(clearWithdrawalLocalState({ id: 'actual-user', email: 'withdraw@example.test' })).toBe(false);
});
