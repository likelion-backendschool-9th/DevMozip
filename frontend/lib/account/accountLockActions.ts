'use server';
import { backendFetch } from '@/lib/auth/backend';
import type { AccountLockStatus } from '@/types/account';
import {
    ACCOUNT_LOCK_CONNECT_ERROR, ACCOUNT_LOCK_INPUT_ERROR, ACCOUNT_LOCK_LOAD_ERROR, ACCOUNT_LOCK_SAVE_ERROR, accountLockError, parseAccountLock,
} from './accountLock';

const PATH = '/api/v1/users/me/account-lock';

async function readAccountLock(response: Response, fallback: string, expected?: boolean): Promise<AccountLockStatus | { error: string }> {
    if (!response.ok) return { error: accountLockError(response.status, fallback) };
    try {
        const value = parseAccountLock(await response.json());
        // 변경 응답은 요청한 최종 값이어야 한다. 다르면 저장 결과를 알 수 없으므로 실패로 본다.
        return value && (expected === undefined || value.isPrivate === expected) ? value : { error: fallback };
    } catch { return { error: fallback }; }
}

// 개인정보 설정의 계정 공개 영역(WBS 17.7). 요청자는 백엔드가 쿠키의 액세스 토큰으로 정한다.
export async function accountLockStatusAction(): Promise<AccountLockStatus | { error: string }> {
    let response: Response;
    try { response = await backendFetch(PATH); }
    catch { return { error: ACCOUNT_LOCK_CONNECT_ERROR }; }
    return readAccountLock(response, ACCOUNT_LOCK_LOAD_ERROR);
}

// 서버 액션은 화면을 거치지 않고도 호출될 수 있으므로 boolean이 아니면 백엔드를 부르지 않는다.
export async function updateAccountLockAction(isPrivate: boolean): Promise<AccountLockStatus | { error: string }> {
    if (typeof isPrivate !== 'boolean') return { error: ACCOUNT_LOCK_INPUT_ERROR };
    let response: Response;
    try {
        response = await backendFetch(PATH, {
            method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ isPrivate }),
        });
    } catch { return { error: ACCOUNT_LOCK_CONNECT_ERROR }; }
    return readAccountLock(response, ACCOUNT_LOCK_SAVE_ERROR, isPrivate);
}
