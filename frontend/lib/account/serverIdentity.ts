import { demoAccounts } from '@/mocks/auth';
import type { AuthUser } from '@/types/auth';
import type { ServerIdentity } from '@/types/session';

// 프로필 데모 표시는 재사용하되 권한은 오직 백엔드 응답에서 가져온다.
export function projectServerIdentity(identity: ServerIdentity): AuthUser {
    const template = demoAccounts.find(user => user.email === identity.username) ?? demoAccounts[0];
    return { ...template, id: identity.role === 'ADMIN' ? 'demo-admin' : template.role === 'admin' ? 'demo-user' : template.id,
        email: identity.username, role: identity.role === 'ADMIN' ? 'admin' : identity.role === 'PREMIUM' ? 'premium' : 'free', onboardingCompleted: true };
}
