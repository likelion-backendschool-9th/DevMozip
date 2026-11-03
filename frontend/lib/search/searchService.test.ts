import { describe,expect,it } from 'vitest';
import { activeFilterCount, changeSearchTarget, parseSearch, searchDevelopers, searchProjects } from '@/lib/search/searchService';
import { applyProfileAction, freshProfileState } from '@/lib/profiles/profileService';
import { projects } from '@/mocks/home';
const filters=(query='')=>parseSearch(new URLSearchParams(query));
const profiles=()=>Object.values(freshProfileState().profiles);
describe('통합 검색 조건 및 URL',()=>{
 it('알 수 없는 값은 유효한 기본값으로 복원하고 기술 중복을 제거한다',()=>{const f=filters('type=missing&job=unknown&career=99&available=maybe&stack=React&stack=React&stack=unknown');expect(f.target).toBe('developer');expect(f.job).toBe('');expect(f.career).toBe('');expect(f.available).toBe('');expect(f.stacks).toEqual(['React','unknown']);});
 it('대상 전환은 검색어를 유지하고 이전 대상 필터를 초기화한다',()=>{const p=changeSearchTarget(new URLSearchParams('type=developers&job=백엔드 개발자&tab=following&stack=Java'),'project',' Spring Boot ');expect(p.get('q')).toBe('Spring Boot');expect(p.get('type')).toBe('projects');expect(p.has('job')).toBe(false);expect(p.has('tab')).toBe(false);expect(p.has('stack')).toBe(false);});
 it('같은 대상에서 새 검색어로 검색하면 기존 상세 필터를 유지한다',()=>{const p=changeSearchTarget(new URLSearchParams('type=projects&status=모집 중&stack=React'),'project','Flowmate');expect(p.get('status')).toBe('모집 중');expect(p.getAll('stack')).toEqual(['React']);expect(p.get('q')).toBe('Flowmate');});
 it('프로젝트 검색은 개발자 전용 조건을 무시한다',()=>{const f=filters('type=projects&tab=following&job=백엔드 개발자&available=yes');expect(f.scope).toBe('all');expect(f.job).toBe('');expect(f.available).toBe('');});
 it('선택된 상세 필터 수를 계산하고 검색어는 포함하지 않는다',()=>{expect(activeFilterCount(filters('q=React&stack=React&stack=TypeScript&available=yes&career=3년 차'))).toBe(4);});
});
describe('개발자 Mock 검색',()=>{
 it('닉네임과 대소문자/전각이 다른 기술 검색어를 처리한다',()=>{expect(searchDevelopers(profiles(),filters('q=유나'),[],false).map(p=>p.id)).toEqual(['yuna']);expect(searchDevelopers(profiles(),filters('q=ｒｅａｃｔ'),[],false).some(p=>p.id==='yuna')).toBe(true);});
 it('직군·경력·분야·협업 상태·기술을 모두 결합한다',()=>{const f=filters('job=프론트엔드 개발자&career=3년 차&interest=커뮤니티&available=yes&stack=React&stack=Figma');expect(searchDevelopers(profiles(),f,[],true).map(p=>p.id)).toEqual(['yuna']);});
 it('복수 기술은 모두 포함해야 한다',()=>{const result=searchDevelopers(profiles(),filters('stack=React&stack=Figma'),[],true);expect(result.length).toBeGreaterThan(0);expect(result.every(p=>p.stacks.includes('React')&&p.stacks.includes('Figma'))).toBe(true);});
 it('협업 불가능 필터도 결과에 반영한다',()=>{const result=searchDevelopers(profiles(),filters('available=no'),[],true);expect(result.length).toBeGreaterThan(0);expect(result.every(p=>!p.available)).toBe(true);});
 it('팔로잉은 로그인 계정의 관계만 반환하고 비로그인은 빈 목록이다',()=>{const f=filters('tab=following');expect(searchDevelopers(profiles(),f,['minjun'],true).map(p=>p.id)).toEqual(['minjun']);expect(searchDevelopers(profiles(),f,['minjun'],false)).toEqual([]);});
 it('팔로우 변경과 프로필 수정은 검색 결과에 즉시 반영된다',()=>{let state=freshProfileState();state=applyProfileAction(state,'demo-user',{type:'follow',targetId:'yuna'});expect(searchDevelopers(Object.values(state.profiles),filters('tab=following'),state.following['demo-user'],true).some(p=>p.id==='yuna')).toBe(false);state=applyProfileAction(state,'yuna',{type:'profile',patch:{...state.profiles.yuna,stacks:['Python'],available:false}});expect(searchDevelopers(Object.values(state.profiles),filters('q=유나&stack=Python&available=no'),[],true).map(p=>p.id)).toEqual(['yuna']);});
 it('일치하지 않는 검색어는 빈 결과다',()=>{expect(searchDevelopers(profiles(),filters('q=존재하지않는개발자'),[],true)).toEqual([]);});
});
describe('프로젝트 Mock 검색',()=>{
 it('프로젝트명은 대소문자 구분 없이 검색한다',()=>{expect(searchProjects(projects,filters('type=projects&q=FLOWMATE')).map(p=>p.id)).toEqual(['flowmate']);});
 it('기술·모집 직군·분야·상태 조건을 결합한다',()=>{expect(searchProjects(projects,filters('type=projects&role=백엔드&field=핀테크&status=모집 중&stack=Java&stack=Spring Boot')).map(p=>p.id)).toEqual(['pocket']);});
 it('상태가 없는 기존 Mock 공고는 모집 중으로 취급한다',()=>{expect(searchProjects(projects,filters('type=projects&status=모집 중')).some(p=>p.id==='flowmate')).toBe(true);expect(searchProjects(projects,filters('type=projects&status=프로젝트 종료')).every(p=>p.status==='프로젝트 종료')).toBe(true);});
 it('검색으로 원본 목록을 변경하지 않고 빈 결과를 처리한다',()=>{const original=JSON.stringify(projects);expect(searchProjects(projects,filters('type=projects&q=없는프로젝트'))).toEqual([]);searchProjects(projects,filters('type=projects'));expect(JSON.stringify(projects)).toBe(original);});
});

describe('분야 복수 선택',()=>{it('개발자 분야는 OR, 스택은 AND로 조합한다',()=>{const f=filters('interest=교육&interest=커뮤니티&stack=React');const result=searchDevelopers(profiles(),f,[],true);expect(result.length).toBeGreaterThan(0);expect(result.every(p=>p.stacks.includes('React')&&p.interests.some(i=>['교육','커뮤니티'].includes(i)))).toBe(true);expect(activeFilterCount(f)).toBe(3);});it('프로젝트 분야 복수 선택 및 반복 URL 복원을 지원한다',()=>{const f=filters('type=projects&field=교육&field=핀테크&field=교육');expect(f.fields).toEqual(['교육','핀테크']);expect(searchProjects(projects,f).every(p=>['교육','핀테크'].includes(p.field))).toBe(true);});});
