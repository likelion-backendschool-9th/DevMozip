import { createLocalStore, derived, useStore } from '@/lib/localStore';
import { moderateProfile, type ModerationAction } from '@/lib/profiles/moderationService';
import { allProfiles, applyProfileAction, freshProfileState, loadProfiles, saveProfiles } from '@/lib/profiles/profileService';
import { authSources, getAdministration, getPublicUsers, getUser, resetProfileNames, syncProfileName } from '@/lib/state/authStore';
import { notify } from '@/lib/state/prototypeStore';
import type { Collaboration, ProfileAction, ProfileState } from '@/types/profile';

// 프로필 · 포트폴리오 · 글 · 댓글 · 리뷰 · 팔로우.
const profileStore = createLocalStore(loadProfiles);

export const profileSources = [...authSources, profileStore];

/** 가입한 Mock 계정의 기본 프로필까지 합친 상태 */
const profileState = derived(profileSources, (): ProfileState => ({ ...profileStore.get(), profiles: allProfiles(profileStore.get(), getPublicUsers()) }));

export const getProfileState = profileState.get;

function commit(next: ProfileState) {
    profileStore.set(next);
    return saveProfiles(next);
}

export function profileAct(action: ProfileAction, history?: Collaboration[], now = new Date()) {
    try {
        if (action.type === 'review') throw new Error('프로젝트 리뷰 요청 링크에서 작성해 주세요.');
        const user = getUser();
        const current = getProfileState();
        const next = applyProfileAction(current, user?.id || null, action, now, history, getAdministration().keywords);
        if (action.type === 'social-view' && JSON.stringify(next) === JSON.stringify(current)) return true;
        const saved = commit(next);
        if ((action.type === 'profile' || action.type === 'onboarding') && user) syncProfileName(user.id, next.profiles[user.id].nickname);
        if (action.type !== 'social-view' && action.type !== 'follow-publish') notify(saved ? '변경사항을 반영했습니다.' : '변경했지만 저장 공간을 사용할 수 없어 이번 세션에만 유지됩니다.');
        return true;
    } catch (error) {
        notify(error instanceof Error ? error.message : '변경하지 못했습니다.');
        return false;
    }
}

function moderate(action: ModerationAction) {
    try {
        const saved = commit(moderateProfile(getProfileState(), getUser(), action));
        notify(saved ? '콘텐츠 관리를 반영했습니다.' : '현재 세션에만 유지됩니다.');
        return true;
    } catch (e) {
        notify(e instanceof Error ? e.message : '변경 실패');
        return false;
    }
}

function reset() {
    resetProfileNames();
    commit(freshProfileState());
}

const profiles = derived(profileSources, () => ({
    state: getProfileState(),
    act: profileAct,
    moderate,
    reset,
}));

export function useProfiles() {
    return useStore(profiles);
}
