import { demoAccounts } from '@/mocks/auth';
import { HOME_KEY, freshHomeState } from '@/lib/matching/homeService';
import { clearLoginPrompt } from '@/lib/matching/loginPrompt';
import type { AuthUser } from '@/types/auth';

// 서버 회원은 이메일별로 보관한 초안만 제거한다. 다른 계정과 Mock 원본은 유지한다.
export function clearWithdrawalLocalState(user: Pick<AuthUser, 'id' | 'email'> | null) {
    if (!user) return true;
    const owner = demoAccounts.some(account => account.id === user.id && account.email === user.email)
        ? user.id : `account:${user.email.toLowerCase()}`;
    try {
        for (const key of [HOME_KEY, `devmozip:empty-mode:v1:${HOME_KEY}`]) {
            const raw = localStorage.getItem(key);
            if (!raw) continue;
            const state = JSON.parse(raw);
            if (state?.version !== 1) continue;
            if (state.chat?.conversations) delete state.chat.conversations[owner];
            if (state.chat?.active) delete state.chat.active[owner];
            if (state.matching?.usage) delete state.matching.usage[owner];
            if (state.matching?.last) delete state.matching.last[owner];
            if (state.inputOwner === owner) {
                state.input = freshHomeState().input;
                delete state.inputOwner;
            }
            localStorage.setItem(key, JSON.stringify(state));
        }
        clearLoginPrompt();
        return true;
    } catch { return false; }
}
