import { createLocalStore, derived, useStore } from '@/lib/localStore';
import { applyKickAction } from '@/lib/projects/chatKickService';
import { projectHistory } from '@/lib/projects/projectSelectors';
import { syncProjectState } from '@/lib/projects/projectManagementService';
import { allProjects, applyRecruitmentAction, loadRecruitmentState, saveRecruitmentState } from '@/lib/projects/projectService';
import { validateReviewSubmission } from '@/lib/projects/projectReviewService';
import { getAdministration, getUser } from '@/lib/state/authStore';
import { getProfileState, profileAct, profileSources } from '@/lib/state/profileStore';
import { notify } from '@/lib/state/prototypeStore';
import type { ChatKickAction } from '@/types/chatKick';
import type { TeamReviewInput } from '@/types/projectReview';
import type { RecruitmentAction, RecruitmentState } from '@/types/project';

interface Result {
    ok: boolean;
    projectId?: string;
    error?: string;
}

// 모집 공고 · 지원 · 팀원 · 리뷰 요청 · 프로젝트 알림 · 날짜 시뮬레이션.
const projectStore = createLocalStore(() => {
    const loaded = loadRecruitmentState();
    return syncProjectState(loaded, loaded.simulationDate ? new Date(loaded.simulationDate) : new Date(), getProfileState());
}, true);

export const projectSources = [...profileSources, projectStore];

export const getProjectState = projectStore.get;

/** 날짜 시뮬레이션 중이면 그 날짜, 아니면 지금 */
export function projectClock() {
    const simulationDate = projectStore.get().simulationDate;
    return simulationDate ? new Date(simulationDate) : new Date();
}

function commit(next: RecruitmentState) {
    projectStore.set(next);
    return saveRecruitmentState(next);
}

/** 모집 기한 · 리뷰 요청 등을 지금 시각과 프로필 리뷰에 맞춘다. 달라진 것이 없으면 저장하지 않는다 */
export function syncProjects() {
    const next = syncProjectState(projectStore.get(), projectClock(), getProfileState());
    if (JSON.stringify(next) !== JSON.stringify(projectStore.get())) commit(next);
}

/** 처음 불러온 상태를 저장하고 1분마다 syncProjects를 실행한다 — ProjectSync가 호출한다 */
export function startProjectSync() {
    saveRecruitmentState(projectStore.get());
    const timer = window.setInterval(syncProjects, 60000);
    return () => window.clearInterval(timer);
}

function act(action: RecruitmentAction): Result {
    try {
        const result = applyRecruitmentAction(projectStore.get(), action, getUser(), getProfileState(), projectClock());
        const saved = commit(result.state);
        if (action.type === 'create' && result.projectId) profileAct({ type: 'follow-publish', kind: 'project', recordId: result.projectId, title: action.input.title });
        notify(saved ? result.message : `${result.message} 저장 공간을 사용할 수 없어 이번 세션에만 유지됩니다.`);
        return { ok: true, projectId: result.projectId };
    } catch (e) {
        const error = e instanceof Error ? e.message : '요청을 처리하지 못했습니다.';
        notify(error);
        return { ok: false, error };
    }
}

function kick(action: ChatKickAction): Result {
    try {
        const date = projectClock();
        const synced = syncProjectState(projectStore.get(), date, getProfileState());
        if (JSON.stringify(synced) !== JSON.stringify(projectStore.get())) commit(synced);
        const saved = commit(applyKickAction(projectStore.get(), action, getUser(), getProfileState(), date));
        notify(saved ? action.type === 'start' ? '강퇴 투표를 시작했습니다.' : '투표를 반영했습니다.' : '현재 세션에만 투표를 반영했습니다.');
        return { ok: true };
    } catch (e) {
        const error = e instanceof Error ? e.message : '투표를 처리하지 못했습니다.';
        notify(error);
        return { ok: false, error };
    }
}

function simulate(date: string | null) {
    if (!getUser()) {
        notify('로그인 후 시뮬레이션을 이용해 주세요.');
        return;
    }
    if (date !== null && !Number.isFinite(Date.parse(date))) {
        notify('시뮬레이션 날짜를 확인해 주세요.');
        return;
    }
    const next = structuredClone(projectStore.get());
    if (date) next.simulationDate = new Date(date).toISOString();
    else delete next.simulationDate;
    const saved = commit(syncProjectState(next, date ? new Date(date) : new Date(), getProfileState()));
    notify(saved ? 'Mock 날짜 비교를 실행했습니다.' : '현재 세션에만 날짜를 반영했습니다.');
}

function submitReviews(projectId: string, requestId: string, token: string, entries: TeamReviewInput[]): Result {
    try {
        const date = projectClock();
        validateReviewSubmission(projectStore.get(), getProfileState(), getUser()?.id || null, projectId, requestId, token, entries, getAdministration().keywords, date);
        if (!profileAct({ type: 'reviews-batch', projectId, entries }, projectHistory(projectStore.get()), date)) return { ok: false, error: '팀원 평가 내용을 확인해 주세요.' };
        const next = structuredClone(projectStore.get());
        next.reviewRequests.find(r => r.id === requestId)!.usedAt = date.toISOString();
        for (const n of next.notifications) if (n.reviewRequestId === requestId) n.resolvedAt = date.toISOString();
        const saved = commit(next);
        notify(saved ? '팀원 리뷰를 모두 제출했습니다.' : '리뷰는 현재 세션에 반영했지만 저장할 수 없습니다.');
        return { ok: true };
    } catch (e) {
        const error = e instanceof Error ? e.message : '리뷰를 제출하지 못했습니다.';
        notify(error);
        return { ok: false, error };
    }
}

const projects = derived(projectSources, () => ({
    state: projectStore.get(),
    now: projectClock(),
    kick,
    projects: allProjects(projectStore.get()),
    history: projectHistory(projectStore.get()),
    act,
    simulate,
    submitReviews,
}));

export function useProjects() {
    return useStore(projects);
}
