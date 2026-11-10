import type { AccountLockStatus } from '@/types/account';

/** 개인정보 설정의 계정 공개 영역(WBS 17.7 계정 잠금). 서버 액션과 화면이 함께 쓰는 응답 해석·상태 전이 */
export type AccountLockResult = AccountLockStatus | { error: string };
export type AccountLockState = { status: 'loading' } | { status: 'ready'; isPrivate: boolean } | { status: 'error'; message: string };

export const ACCOUNT_LOCK_LOAD_ERROR = '공개 설정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.';
export const ACCOUNT_LOCK_SAVE_ERROR = '공개 설정을 바꾸지 못했습니다. 잠시 후 다시 시도해 주세요.';
export const ACCOUNT_LOCK_CONNECT_ERROR = '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
export const ACCOUNT_LOCK_INPUT_ERROR = '공개 설정을 확인해 주세요.';

// 백엔드 오류 본문은 그대로 보여 주지 않고 상태별 안내로 바꾼다. 401은 다른 영역과 같이 로그인 만료로 안내한다.
const statusMessages: Record<number, string> = {
    400: ACCOUNT_LOCK_INPUT_ERROR,
    401: '로그인이 만료되었습니다. 다시 로그인해 주세요.',
    403: '이 계정 상태에서는 공개 설정을 이용할 수 없습니다.',
};

export function accountLockError(status: number, fallback: string) {
    return statusMessages[status] ?? fallback;
}

/** isPrivate가 boolean인 응답만 받고 그 밖의 필드는 버린다. */
export function parseAccountLock(body: unknown): AccountLockStatus | null {
    if (!body || typeof body !== 'object' || Array.isArray(body)) return null;
    const { isPrivate } = body as { isPrivate?: unknown };
    return typeof isPrivate === 'boolean' ? { isPrivate } : null;
}

/** 서버 값을 불러와 화면 상태로 바꾼다. 실패하면 재시도할 수 있는 오류 상태가 된다. */
export async function loadAccountLock(load: () => Promise<AccountLockResult>): Promise<AccountLockState> {
    try {
        const result = await load();
        return 'error' in result ? { status: 'error', message: result.error } : { status: 'ready', isPrivate: result.isPrivate };
    } catch {
        return { status: 'error', message: ACCOUNT_LOCK_LOAD_ERROR };
    }
}

/** 확인 창의 전환 요청. 원하는 최종 값을 보낸다. 실패하면 이전 값을 그대로 두고 안내를 돌려준다. */
export async function changeAccountLock(current: boolean, change: (isPrivate: boolean) => Promise<AccountLockResult>): Promise<{ isPrivate: boolean; error: string }> {
    try {
        const result = await change(!current);
        return 'error' in result ? { isPrivate: current, error: result.error } : { isPrivate: result.isPrivate, error: '' };
    } catch {
        return { isPrivate: current, error: ACCOUNT_LOCK_SAVE_ERROR };
    }
}
