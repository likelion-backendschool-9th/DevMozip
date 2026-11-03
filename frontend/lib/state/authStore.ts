import { logoutAction } from '@/lib/auth/actions';
import { projectServerIdentity } from '@/lib/account/serverIdentity';
import type { SessionSnapshot } from '@/types/session';
import { createLocalStore, derived, useStore } from '@/lib/localStore';
import { loadProfiles } from '@/lib/profiles/profileService';
import { canViewAccount } from '@/lib/profiles/privacyService';
import { applyAdministration, effectiveUser, isSuspended, loadAdministration, saveAdministration, freshAdministration } from '@/lib/administration/administrationService';
import { authenticate, clearSession, createAccount, loadAccounts, loadSession, persistAccounts, persistSession, emailAvailable } from '@/lib/account/authService';
import { applyAccountArea, freshAccountArea, loadAccountArea, onboardingComplete, passwordChangeErrors, saveAccountArea } from '@/lib/account/accountAreaService';
import { onboardingErrors } from '@/lib/account/onboardingService';
import { demoAccounts } from '@/mocks/auth';
import { interestOptions } from '@/mocks/home';
import { notify, prototypeSources } from '@/lib/state/prototypeStore';
import type { AdministrationAction } from '@/types/administration';
import type { AccountAreaAction, AccountAreaState, OnboardingDraft, PasswordChangeInput } from '@/types/account';
import type { AccountRole, AuthUser, SignupInput } from '@/types/auth';

type PreferenceAction = Exclude<AccountAreaAction, { type: 'onboarding-complete' } | { type: 'password-confirmed' }>;

// 가입 계정 · 로그인 세션 · 관리자 제재 · 계정 설정 · 프로필에서 바꾼 닉네임.
const serverSessionStore = createLocalStore<SessionSnapshot>(() => ({ mode: 'mock', identity: null }));
export function syncServerSession(snapshot: SessionSnapshot) { serverSessionStore.set(snapshot); }

const accountsStore = createLocalStore(loadAccounts);
const sessionStore = createLocalStore(() => loadSession(accountsStore.get()));
const administrationStore = createLocalStore(loadAdministration);
const accountAreaStore = createLocalStore(loadAccountArea);
const profileNamesStore = createLocalStore<Record<string, string>>(() => Object.fromEntries(Object.values(loadProfiles().profiles).map(p => [p.id, p.nickname])));

export const authSources = [serverSessionStore, ...prototypeSources, accountsStore, sessionStore, administrationStore, accountAreaStore, profileNamesStore];

/** 정지되지 않은 로그인 계정(관리자 경고 등 반영) */
const accountUser = derived(authSources, () => {
    const server = serverSessionStore.get();
    if (server.mode === 'server') return server.identity ? projectServerIdentity(server.identity) : null;
    const sessionUser = sessionStore.get();
    return sessionUser && !isSuspended(administrationStore.get(), sessionUser.id) ? effectiveUser(sessionUser, administrationStore.get()) : null;
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

export function resetProfileNames() {
    profileNamesStore.set({});
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

function resetAdministration() {
    const next = freshAdministration();
    administrationStore.set(next);
    saveAdministration(next);
}

function start(next: AuthUser, remember: boolean) {
    if (serverSessionStore.get().mode === 'server') return false;
    if (isSuspended(administrationStore.get(), next.id)) {
        notify('정지된 회원입니다. 관리자 데모에서 해제해 주세요.');
        return false;
    }
    sessionStore.set(next);
    if (!persistSession(next, remember)) notify('로그인되었지만 브라우저 저장 공간을 사용할 수 없어 현재 화면에서만 유지됩니다.');
    return true;
}

function logout() {
    if (serverSessionStore.get().mode === 'server') {
        const previous = serverSessionStore.get();
        serverSessionStore.set({ mode: 'server', identity: null });
        void logoutAction().then(result => { if (result?.error) { serverSessionStore.set(previous); notify(result.error); } }).catch(() => { serverSessionStore.set(previous); notify('로그아웃을 완료하지 못했습니다.'); });
        return;
    }
    clearSession();
    sessionStore.set(null);
}

async function login(email: string, password: string, remember: boolean) {
    if (serverSessionStore.get().mode === 'server') throw new Error('서버 로그인 폼을 이용해 주세요.');
    const next = await authenticate(email, password, accountsStore.get());
    if (isSuspended(administrationStore.get(), next.id)) throw new Error('정지된 회원입니다. 관리자에게 문의해 주세요.');
    start(next, remember);
}

async function signup(input: SignupInput) {
    if (serverSessionStore.get().mode === 'server') throw new Error('실제 회원가입은 아직 제공하지 않습니다. 임시 인증 계정을 이용해 주세요.');
    const accounts = accountsStore.get();
    const account = await createAccount(input, accounts);
    const next = [...accounts, account];
    if (!persistAccounts(next)) throw new Error('브라우저에 가입 정보를 저장하지 못했습니다. 저장 공간 설정을 확인해 주세요.');
    accountsStore.set(next);
    start(account.user, true);
}

function completeOnboarding(draft: OnboardingDraft) {
    try {
        const user = getUser();
        if (!user) throw new Error('로그인 후 이용해 주세요.');
        const errors = onboardingErrors(draft, 3, getPublicUsers().some(u => u.id !== user.id && u.nickname.toLowerCase() === draft.nickname.trim().toLowerCase()));
        if (errors.length) throw new Error(errors[0]);
        const updated = {
            ...sessionStore.get()!,
            nickname: draft.nickname.trim(),
            job: draft.jobs[0],
            career: draft.career,
            stacks: [...draft.stacks],
            interests: draft.interests.filter(i => interestOptions.slice(1).includes(i)),
            available: draft.available,
            onboardingCompleted: true,
        };
        const next = accountsStore.get().map(a => a.user.id === updated.id ? { ...a, user: updated } : a);
        accountsStore.set(next);
        sessionStore.set(updated);
        if (!persistAccounts(next)) notify('프로필은 반영했지만 계정 변경을 저장할 수 없어 현재 세션에만 유지됩니다.');
        commitArea(applyAccountArea(accountAreaStore.get(), user.id, { type: 'onboarding-complete' }));
        return true;
    } catch (e) {
        notify(e instanceof Error ? e.message : '온보딩 완료 실패');
        return false;
    }
}

async function confirmPasswordChange(input: PasswordChangeInput) {
    if (serverSessionStore.get().mode === 'server') throw new Error('실제 비밀번호 변경은 아직 제공하지 않습니다.');
    const user = getUser();
    if (!user) throw new Error('로그인 후 이용해 주세요.');
    const errors = passwordChangeErrors(input);
    if (errors.length) throw new Error(errors[0]);
    await authenticate(user.email, input.currentPassword, accountsStore.get());
    commitArea(applyAccountArea(accountAreaStore.get(), user.id, { type: 'password-confirmed' }));
}

function switchDemo(role: AccountRole | 'guest') {
    if (serverSessionStore.get().mode === 'server') { notify('운영에서는 데모 계정으로 전환할 수 없습니다.'); return false; }
    if (role === 'guest') {
        logout();
        return true;
    }
    const demo = demoAccounts.find(a => a.role === role);
    return demo ? start(demo, true) : false;
}

const auth = derived(authSources, () => {
    const user = getUser();
    return {
        mode: serverSessionStore.get().mode,
        syncProfileName,
        resetProfileNames,
        canViewProfile,
        user,
        accountUser: accountUser.get(),
        publicUsers: getPublicUsers(),
        accountArea: accountAreaStore.get(),
        needsOnboarding: !!user && !onboardingComplete(accountAreaStore.get(), user),
        accountAct,
        completeOnboarding,
        confirmPasswordChange,
        resetAccountArea: () => commitArea(freshAccountArea()),
        administration: administrationStore.get(),
        administer,
        resetAdministration,
        login,
        signup,
        logout,
        switchDemo,
        checkEmail: (email: string) => emailAvailable(email, accountsStore.get()),
        checkNickname: (nickname: string) => !getPublicUsers().some(u => u.nickname.toLowerCase() === nickname.trim().toLowerCase()),
    };
});

export function useAuth() {
    return useStore(auth);
}
