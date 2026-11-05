import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest';
import { mockVisibilityStore, demoStorageKey } from './mockVisibility';
import { freshProfileState, loadProfiles, saveProfiles, allProfiles, PROFILE_KEY } from '@/lib/profiles/profileService';
import { freshRecruitmentState, loadRecruitmentState, saveRecruitmentState, PROJECT_KEY } from '@/lib/projects/projectService';
import { allProjects } from '@/lib/projects/projectSelectors';
import { loadMessaging } from '@/lib/messaging/messagingService';
import { loadAdministration } from '@/lib/administration/administrationService';
import { createLocalStore, resetMockStores } from '@/lib/localStore';
import { demoAccounts } from '@/mocks/auth';

let values: Map<string,string>;
beforeEach(() => {
    values = new Map();
    vi.stubGlobal('localStorage', { getItem:(key:string)=>values.get(key)??null, setItem:(key:string,value:string)=>{values.set(key,value);} });
    mockVisibilityStore.setEnabled(true);
});
afterEach(() => { mockVisibilityStore.setEnabled(true); vi.unstubAllGlobals(); });

describe('샘플 데이터 없는 작업 공간', () => {
    it('OFF는 원래 프로필·게시글·프로젝트 저장소를 읽거나 덮어쓰지 않는다', () => {
        saveProfiles(freshProfileState()); saveRecruitmentState(freshRecruitmentState());
        const beforeProfiles=values.get(PROFILE_KEY); const beforeProjects=values.get(PROJECT_KEY);
        expect(loadProfiles().posts.length).toBeGreaterThan(0);
        expect(allProjects(loadRecruitmentState()).length).toBeGreaterThan(0);
        mockVisibilityStore.setEnabled(false);
        const profile=loadProfiles(); const project=loadRecruitmentState();
        expect(profile.posts).toEqual([]); expect(profile.portfolios).toEqual([]); expect(profile.reviews).toEqual([]);
        expect(allProfiles(profile,demoAccounts)).toEqual({}); expect(allProjects(project)).toEqual([]);
        expect(loadMessaging().conversations).toEqual([]); expect(loadAdministration().payments).toEqual([]);
        saveProfiles(profile); saveRecruitmentState(project);
        expect(values.get(PROFILE_KEY)).toBe(beforeProfiles); expect(values.get(PROJECT_KEY)).toBe(beforeProjects);
        mockVisibilityStore.setEnabled(true);
        expect(loadProfiles().posts.length).toBeGreaterThan(0); expect(allProjects(loadRecruitmentState()).length).toBeGreaterThan(0);
    });
    it('OFF에서 직접 작성한 데이터는 ON과 분리되고 OFF 복귀 시 유지된다', () => {
        mockVisibilityStore.setEnabled(false);
        const state=freshProfileState();
        state.profiles['author']={id:'author',nickname:'직접입력',handle:'author',job:'',career:'',bio:'',introduction:'',joinedAt:'',github:'',blog:'',contact:'',available:true,stacks:[],interests:[],color:'lavender'};
        state.posts.push({id:'my-post',authorId:'author',title:'직접 작성한 글',body:'본문',category:'자유',tags:[],likes:0,comments:0,color:'lavender',hidden:false});
        expect(saveProfiles(state)).toBe(true); expect(loadProfiles().posts[0].title).toBe('직접 작성한 글');
        expect(demoStorageKey(PROFILE_KEY)).not.toBe(PROFILE_KEY);
        mockVisibilityStore.setEnabled(true); expect(loadProfiles().posts.some(p=>p.id==='my-post')).toBe(false);
        mockVisibilityStore.setEnabled(false); expect(loadProfiles().posts[0].id).toBe('my-post');
    });
    it('OFF 저장 데이터가 손상돼도 샘플 데이터로 되돌아가지 않는다', () => {
        mockVisibilityStore.setEnabled(false);
        values.set(demoStorageKey(PROFILE_KEY),'broken'); values.set(demoStorageKey(PROJECT_KEY),'broken');
        expect(loadProfiles().posts).toEqual([]); expect(allProjects(loadRecruitmentState())).toEqual([]);
    });
    it('모드 캐시 초기화는 로그인 세션을 초기화하지 않는다', () => {
        const identity={email:'real@user.test'}; const auth=createLocalStore(()=>identity);
        let modeValue='before'; const domain=createLocalStore(()=>modeValue,true);
        expect(auth.get()).toBe(identity); expect(domain.get()).toBe('before');
        modeValue='after'; resetMockStores();
        expect(auth.get()).toBe(identity); expect(domain.get()).toBe('after');
    });
});
