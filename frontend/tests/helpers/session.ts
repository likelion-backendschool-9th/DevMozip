import { syncServerSession } from '@/lib/state/authStore';
import { demoAccounts } from '@/mocks/auth';
import type { ServerRole } from '@/types/session';

const roles: Record<string, ServerRole> = { free: 'USER', premium: 'PREMIUM', admin: 'ADMIN' };

/** 백엔드가 확인한 데모 계정 세션을 화면 테스트에 넣는다. resetLocalStores() 뒤에 호출한다 */
export function signIn(userId?: string) {
    const account = demoAccounts.find(a => a.id === userId);
    if (userId && !account) throw new Error(`서버에 등록된 데모 계정이 아닙니다: ${userId}`);
    syncServerSession({ identity: account ? { username: account.email, role: roles[account.role] } : null });
}
