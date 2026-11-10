import { describe, expect, it, vi } from 'vitest';
import { changeAccountLock, loadAccountLock, parseAccountLock } from './accountLock';

describe('계정 공개 영역 상태', () => {
    it('불러온 값을 표시 상태로 바꾸고, 실패하면 재시도할 수 있는 오류 상태가 된다', async () => {
        expect(await loadAccountLock(async () => ({ isPrivate: true }))).toEqual({ status: 'ready', isPrivate: true });
        expect(await loadAccountLock(async () => ({ error: '로그인이 만료되었습니다. 다시 로그인해 주세요.' })))
            .toEqual({ status: 'error', message: '로그인이 만료되었습니다. 다시 로그인해 주세요.' });
        expect(await loadAccountLock(async () => { throw new Error('action failed'); }))
            .toEqual({ status: 'error', message: '공개 설정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.' });
    });
    it('전환은 현재 값의 반대를 요청하고 서버 응답 값으로 맞춘다', async () => {
        const change = vi.fn(async (isPrivate: boolean) => ({ isPrivate }));
        expect(await changeAccountLock(false, change)).toEqual({ isPrivate: true, error: '' });
        expect(change).toHaveBeenLastCalledWith(true);
        expect(await changeAccountLock(true, change)).toEqual({ isPrivate: false, error: '' });
        expect(change).toHaveBeenLastCalledWith(false);
    });
    it('전환에 실패하면 이전 값을 유지하고 안내를 돌려준다', async () => {
        expect(await changeAccountLock(false, async () => ({ error: '공개 설정을 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.' })))
            .toEqual({ isPrivate: false, error: '공개 설정을 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.' });
        expect(await changeAccountLock(true, async () => { throw new Error('action failed'); }))
            .toEqual({ isPrivate: true, error: '공개 설정을 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.' });
    });
    it('응답 해석은 isPrivate boolean만 받는다', () => {
        expect(parseAccountLock({ isPrivate: false, extra: 1 })).toEqual({ isPrivate: false });
        for (const body of [null, undefined, 'true', [], { isPrivate: 'false' }, { isPrivate: 0 }]) expect(parseAccountLock(body)).toBeNull();
    });
});
