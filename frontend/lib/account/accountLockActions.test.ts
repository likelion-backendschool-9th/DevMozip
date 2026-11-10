import { beforeEach, describe, expect, it, vi } from 'vitest';
const doubles = vi.hoisted(() => ({ fetch: vi.fn() }));
vi.mock('@/lib/auth/backend', () => ({ backendFetch: doubles.fetch }));
import { accountLockStatusAction, updateAccountLockAction } from './accountLockActions';

const PATH = '/api/v1/users/me/account-lock';
beforeEach(() => vi.clearAllMocks());

describe('계정 잠금 서버 액션', () => {
    it('조회는 isPrivate만 남기고 그 밖의 필드는 버린다', async () => {
        doubles.fetch.mockResolvedValueOnce(Response.json({ isPrivate: true, userId: 3, email: 'x@test.local' }));
        expect(await accountLockStatusAction()).toEqual({ isPrivate: true });
        expect(doubles.fetch).toHaveBeenCalledWith(PATH);
    });
    it.each([
        [{ isPrivate: 'true' }], [{ isPrivate: 1 }], [{ private: true }], [{}], [[true]], [null],
    ])('boolean이 아닌 조회 응답 %j은 오류로 바꾼다', async body => {
        doubles.fetch.mockResolvedValueOnce(Response.json(body));
        expect(await accountLockStatusAction()).toEqual({ error: '공개 설정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.' });
    });
    it('JSON이 아닌 응답과 연결 실패를 안내로 바꾼다', async () => {
        doubles.fetch.mockResolvedValueOnce(new Response('<html>', { status: 200 }));
        expect(await accountLockStatusAction()).toEqual({ error: '공개 설정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.' });
        doubles.fetch.mockRejectedValueOnce(new Error('down'));
        expect(await accountLockStatusAction()).toEqual({ error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' });
    });
    it.each([
        [400, '공개 설정을 확인해 주세요.'],
        [401, '로그인이 만료되었습니다. 다시 로그인해 주세요.'],
        [403, '이 계정 상태에서는 공개 설정을 이용할 수 없습니다.'],
        [500, '공개 설정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'],
    ])('조회 오류 %i는 백엔드 본문 대신 상태별 문구로 안내한다', async (status, message) => {
        doubles.fetch.mockResolvedValueOnce(Response.json({ message: '내부 오류 내용' }, { status }));
        expect(await accountLockStatusAction()).toEqual({ error: message });
    });
    it('변경은 원하는 최종 값만 PATCH로 보내고 응답 값을 돌려준다', async () => {
        doubles.fetch.mockResolvedValueOnce(Response.json({ isPrivate: true }));
        expect(await updateAccountLockAction(true)).toEqual({ isPrivate: true });
        expect(doubles.fetch).toHaveBeenCalledWith(PATH, expect.objectContaining({ method: 'PATCH', body: JSON.stringify({ isPrivate: true }) }));
        doubles.fetch.mockResolvedValueOnce(Response.json({ isPrivate: false }));
        expect(await updateAccountLockAction(false)).toEqual({ isPrivate: false });
        expect(doubles.fetch).toHaveBeenLastCalledWith(PATH, expect.objectContaining({ body: JSON.stringify({ isPrivate: false }) }));
    });
    it.each([['true'], [1], [null], [undefined], [{ isPrivate: true }]])('boolean이 아닌 입력 %j은 백엔드를 부르지 않는다', async input => {
        expect(await updateAccountLockAction(input as never)).toEqual({ error: '공개 설정을 확인해 주세요.' });
        expect(doubles.fetch).not.toHaveBeenCalled();
    });
    it('요청과 다른 값이나 잘못된 응답은 저장 실패로 본다', async () => {
        const failure = { error: '공개 설정을 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.' };
        doubles.fetch.mockResolvedValueOnce(Response.json({ isPrivate: false }));
        expect(await updateAccountLockAction(true)).toEqual(failure);
        doubles.fetch.mockResolvedValueOnce(Response.json({ isPrivate: 'true' }));
        expect(await updateAccountLockAction(true)).toEqual(failure);
        doubles.fetch.mockResolvedValueOnce(Response.json({ message: '서버 오류' }, { status: 500 }));
        expect(await updateAccountLockAction(true)).toEqual(failure);
        doubles.fetch.mockResolvedValueOnce(Response.json({ message: '로그인이 필요합니다.' }, { status: 401 }));
        expect(await updateAccountLockAction(true)).toEqual({ error: '로그인이 만료되었습니다. 다시 로그인해 주세요.' });
        doubles.fetch.mockRejectedValueOnce(new Error('down'));
        expect(await updateAccountLockAction(true)).toEqual({ error: '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.' });
    });
});
