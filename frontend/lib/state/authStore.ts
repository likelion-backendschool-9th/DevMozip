import { logoutAction } from '@/lib/auth/actions';
import { projectServerIdentity } from '@/lib/account/serverIdentity';
import type { SessionSnapshot } from '@/types/session';
import { createLocalStore, derived, useStore } from '@/lib/localStore';
import { loadProfiles } from '@/lib/profiles/profileService';
import { canViewAccount } from '@/lib/profiles/privacyService';
import { applyAdministration, effectiveUser, isSuspended, loadAdministration, saveAdministration } from '@/lib/administration/administrationService';
import { loadAccounts, emailAvailable } from '@/lib/account/authService';
import { applyAccountArea, loadAccountArea, onboardingComplete, saveAccountArea } from '@/lib/account/accountAreaService';
import { onboardingErrors } from '@/lib/account/onboardingService';
import { demoAccounts } from '@/mocks/auth';
import { notify, prototypeSources } from '@/lib/state/prototypeStore';
import type { AdministrationAction } from '@/types/administration';
import type { AccountAreaAction, AccountAreaState, OnboardingDraft, PasswordChangeInput } from '@/types/account';
import type { AuthUser, SignupInput } from '@/types/auth';

type PreferenceAction = Exclude<AccountAreaAction, { type: 'onboarding-complete' } | { type: 'password-confirmed' }>;

// 백엔드 로그인 세션 · 이전에 가입한 Mock 계정 · 관리자 제재 · 계정 설정 · 프로필에서 바꾼 닉네임.
// 로그인 상태는 BFF(/api/auth/session)가 백엔드에서 확인한 값만 쓴다. ClientReady가 화면마다 동기화한다.
const serverSessionStore = createLocalStore<SessionSnapshot>(() => ({ identity: null }));
export function syncServerSession(snapshot: SessionSnapshot) { serverSessionStore.set(snapshot); }

const accountsStore = createLocalStore(loadAccounts);
const administrationStore = createLocalStore(loadAdministration);
const accountAreaStore = createLocalStore(loadAccountArea);
const profileNamesStore = createLocalStore<Record<string, string>>(() => Object.fromEntries(Object.values(loadProfiles().profiles).map(p => [p.id, p.nickname])));

export const authSources = [serverSessionStore, ...prototypeSources, accountsStore, administrationStore, accountAreaStore, profileNamesStore];

/**
 * 백엔드가 확인한 로그인 계정에 브라우저 데모 상태(구독 데모·관리자 데모 제재)를 화면 표시용으로만 반영한다.
 * 관리자 역할은 데모 상태로 얻을 수 없고, 실제 인가는 proxy와 백엔드가 토큰 역할로 한다.
 */
const accountUser = derived(authSources, () => {
    const { identity } = serverSessionStore.get();
    const user = identity ? projectServerIdentity(identity) : null;
    return user && !isSuspended(administrationStore.get(), user.id) ? effectiveUser(user, administrationStore.get()) : null;
});

/** 화면에 쓰는 로그인 사용자(프로필에서 바꾼 닉네임 반영) */
const currentUser = derived(authSources, (): AuthUser | null => {
    const user = accountUser.get();
    return user ? { ...user, nickname: profileNamesStore.get()[user.id] || user.nickname } : null;
});

const publicUsers = derived(authSources, () => [...demoAccounts, ...accountsStore.get().map(a => a.user)].map(u => ({ ...effectiveUser(u, administrationStore.get()), nickname: profileNamesStore.get()[u.id] || u.nickname })));

export const getUser = currentUser.get;
export const getPublicUsers = publicUsers.get;
export const getAdministration = administrationStore.get;

export function canViewProfile(targetId: string) {
    return canViewAccount(accountAreaStore.get(), targetId, getUser()?.id || null);
}

export function syncProfileName(id: string, nickname: string) {
    profileNamesStore.set({ ...profileNamesStore.get(), [id]: nickname });
}

function commitArea(next: AccountAreaState) {
    accountAreaStore.set(next);
    if (!saveAccountArea(next)) notify('변경했지만 저장 공간을 사용할 수 없어 현재 세션에만 유지됩니다.');
}

function accountAct(action: PreferenceAction) {
    try {
        commitArea(applyAccountArea(accountAreaStore.get(), accountUser.get()?.id || null, action));
        return true;
    } catch (e) {
        notify(e instanceof Error ? e.message : '변경 실패');
        return false;
    }
}

function administer(action: AdministrationAction) {
    try {
        const next = applyAdministration(administrationStore.get(), getUser(), action, getPublicUsers().map(u => u.id));
        administrationStore.set(next);
        notify(saveAdministration(next) ? 'Mock 상태를 반영했습니다.' : '변경했지만 저장할 수 없어 현재 세션에만 유지됩니다.');
        return true;
    } catch (e) {
        notify(e instanceof Error ? e.message : '변경 실패');
        return false;
    }
}

/** 화면에서는 먼저 로그아웃 상태로 바꾸고, 백엔드 처리에 실패하면 이전 세션으로 되돌린다 */
function logout() {
    const previous = serverSessionStore.get();
    serverSessionStore.set({ identity: null });
    void logoutAction().then(result => { if (result?.error) { serverSessionStore.set(previous); notify(result.error); } }).catch(() => { serverSessionStore.set(previous); notify('로그아웃을 완료하지 못했습니다.'); });
}

// 회원가입·비밀번호 변경 API는 아직 백엔드에 없다.
const signup: (input: SignupInput) => Promise<void> = async () => {
    throw new Error('실제 회원가입은 아직 제공하지 않습니다. 등록된 계정으로 로그인해 주세요.');
};

const confirmPasswordChange: (input: PasswordChangeInput) => Promise<void> = async () => {
    throw new Error('실제 비밀번호 변경은 아직 제공하지 않습니다.');
};

function completeOnboarding(draft: OnboardingDraft) {
    try {
        const user = getUser();
        if (!user) throw new Error('로그인 후 이용해 주세요.');
        const errors = onboardingErrors(draft, 3, getPublicUsers().some(u => u.id !== user.id && u.nickname.toLowerCase() === draft.nickname.trim().toLowerCase()));
        if (errors.length) throw new Error(errors[0]);
        commitArea(applyAccountArea(accountAreaStore.get(), user.id, { type: 'onboarding-complete' }));
        return true;
    } catch (e) {
        notify(e instanceof Error ? e.message : '온보딩 완료 실패');
        return false;
    }
}

const auth = derived(authSources, () => {
    const user = getUser();
    return {
        syncProfileName,
        canViewProfile,
        user,
        accountUser: accountUser.get(),
        publicUsers: getPublicUsers(),
        accountArea: accountAreaStore.get(),
        needsOnboarding: !!user && !onboardingComplete(accountAreaStore.get(), user),
        accountAct,
        completeOnboarding,
        confirmPasswordChange,
        administration: administrationStore.get(),
        administer,
        signup,
        logout,
        checkEmail: (email: string) => emailAvailable(email, accountsStore.get()),
        checkNickname: (nickname: string) => !getPublicUsers().some(u => u.nickname.toLowerCase() === nickname.trim().toLowerCase()),
    };
});

export function useAuth() {
    return useStore(auth);
}
