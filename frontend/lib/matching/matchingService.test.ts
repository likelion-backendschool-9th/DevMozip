import { describe,expect,it } from 'vitest';
import { analyzeMatching,buildMatchingResult,consumeMatching,inputKey,koreanMonth,matchingUsage,parseMatchingState } from '@/lib/matching/matchingService';
import { initialMatchingState } from '@/mocks/matching';
import { developers,projects } from '@/mocks/home';
import { demoAccounts } from '@/mocks/auth';
import { freshHomeState,parseHomeState } from '@/lib/matching/homeService';
import type { RecommendationInput } from '@/types/home';
const now=new Date('2026-10-30T12:00:00+09:00');const member=demoAccounts[0],premium=demoAccounts[1];
const input=():RecommendationInput=>({description:'리액트와 스프링부트로 일정 관리 서비스를 만들고 싶어요. 백엔드 동료를 구해요.',stacks:[],interest:'전체 분야'});
const run=(state=structuredClone(initialMatchingState),user=member,date=now,i=input())=>consumeMatching(state,user,i,developers,projects,date);
describe('자연어 Mock 분석과 추가 질문',()=>{
 it('한국어 기술·분야·직군을 추출한다',()=>{const a=analyzeMatching(input());expect(a.inferredStacks).toEqual(['React','Spring Boot']);expect(a.interests).toContain('생산성');expect(a.role).toBe('백엔드 개발자');expect(a.missing).toEqual([]);});
 it('정보가 부족하면 세 추가 질문을 요구하고 미정 선택도 허용한다',()=>{const i={description:'새로운 서비스를 만들고 싶습니다.',stacks:[],interest:'전체 분야'};expect(analyzeMatching(i).missing).toEqual(['stacks','interest','role']);expect(()=>run(undefined,member,now,i)).toThrow('추가 질문');expect(analyzeMatching({...i,skipStacks:true,skipInterest:true,skipRole:true}).missing).toEqual([]);});
 it('자바스크립트/단어 내부에서 Java/TS/AI를 잘못 감지하지 않는다',()=>{const a=analyzeMatching({description:'JavaScript와 자바스크립트로 projects 사이트를 만들어요.',stacks:[],interest:'전체 분야'});expect(a.stacks).not.toContain('Java');expect(a.stacks).not.toContain('TypeScript');expect(a.interests).not.toContain('AI');});
 it('Node.js의 마침표를 리터럴로 처리한다',()=>{expect(analyzeMatching({description:'Node.js 커뮤니티 API 서버를 만들어요.',stacks:[],interest:'전체 분야'}).stacks).toContain('Node.js');expect(analyzeMatching({description:'NodeXjs 서비스를 준비하고 있어요.',stacks:[],interest:'전체 분야'}).stacks).not.toContain('Node.js');});
 it('여러 직군이 언급되면 직접 선택하는 질문을 남긴다',()=>{expect(analyzeMatching({description:'프론트엔드와 백엔드 동료와 함께 만들고 싶어요.',stacks:[],interest:'전체 분야'}).missing).toContain('role');});
 it('입력 조건에 따라 개발자/유사 프로젝트 순위와 이유가 바뀐다',()=>{const a=buildMatchingResult({description:'생산성 프론트엔드 도구를 만들어요',stacks:['React','TypeScript','Figma'],interest:'생산성'},developers,projects);const b=buildMatchingResult({description:'AI 학습 도우미를 만들고 싶어요',stacks:['Python'],interest:'AI'},developers,projects);expect(a.developers[0].id).toBe('yuna');expect(b.developers[0].id).toBe('seoyeon');expect(a.projects[0].id).toBe('flowmate');expect(b.projects[0].id).toBe('learnloop');expect(a.developers.every(d=>d.score>=55&&d.score<=98&&d.reasons.length>0)).toBe(true);});
});
describe('계정별 월 사용량',()=>{
 it('무료 회원은 정확히 15회 성공 후 차단하며 실패 시 상태를 바꾸지 않는다',()=>{let s=structuredClone(initialMatchingState);for(let n=0;n<15;n++)s=run(s).state;expect(matchingUsage(s,member,now)).toMatchObject({used:15,limit:15,remaining:0});const previous=JSON.stringify(s);expect(()=>run(s)).toThrow('이번 달');expect(JSON.stringify(s)).toBe(previous);});
 it('프리미엄은 임시 확대 한도 100회를 가지며 계정 사용량을 분리한다',()=>{const s=run().state;expect(matchingUsage(s,premium,now)).toMatchObject({used:0,limit:100,remaining:100});const next=run(s,premium).state;expect(matchingUsage(next,premium,now).remaining).toBe(99);expect(matchingUsage(next,member,now).remaining).toBe(14);});
 it('한국 시각 월 경계에서 새 사용량을 적용한다',()=>{expect(koreanMonth(new Date('2026-10-31T14:59:59Z'))).toBe('2026-10');expect(koreanMonth(new Date('2026-10-31T15:00:00Z'))).toBe('2026-11');const s=run().state;expect(matchingUsage(s,member,new Date('2026-11-01T00:00:00+09:00')).remaining).toBe(15);});
 it('비로그인·짧은 입력·잘못된 필드·데이터 없음은 사용량을 소비하지 않는다',()=>{const s=structuredClone(initialMatchingState);expect(()=>consumeMatching(s,null,input(),developers,projects,now)).toThrow('로그인');expect(()=>run(s,member,now,{...input(),description:'짧음'})).toThrow('설명');expect(()=>run(s,member,now,{...input(),stacks:['Unknown']})).toThrow('입력');expect(()=>consumeMatching(s,member,input(),[],[],now)).toThrow('데이터');expect(s).toEqual(initialMatchingState);});
 it('조회한 자신의 프로필은 추천 후보에서 제외한다',()=>{const s=consumeMatching(initialMatchingState,{...member,id:'yuna'},input(),developers,projects,now);expect(s.snapshot.result.developers.some(d=>d.id==='yuna')).toBe(false);});
 it('저장·로그인 재로딩 후 횟수와 결과를 복원한다',()=>{const state=freshHomeState();state.input=input();state.matching=run().state;const loaded=parseHomeState(JSON.stringify(state));expect(matchingUsage(loaded.matching,member,now).remaining).toBe(14);expect(loaded.matching.last[member.id].result.developers.length).toBeGreaterThan(0);});
 it('정보를 수정하면 다른 입력 키이며 순서만 바뀐 기술은 같은 키다',()=>{const i={...input(),stacks:['React','TypeScript']};expect(inputKey(i)).toBe(inputKey({...i,stacks:['TypeScript','React']}));expect(inputKey(i)).not.toBe(inputKey({...i,interest:'AI'}));});
 it('이전 입력을 보존해 새 사용량 필드를 이관한다',()=>{const old={version:1,input:{description:'기존 프로젝트 설명',stacks:['React'],interest:'교육'}};const state=parseHomeState(JSON.stringify(old));expect(state.input.description).toBe('기존 프로젝트 설명');expect(state.matching).toEqual(initialMatchingState);});
 it('손상된 결과만 버리고 유효한 사용량은 보존한다',()=>{const state=run().state;state.last[member.id]={...state.last[member.id],result:null as never};expect(parseMatchingState(state).last).toEqual({});expect(matchingUsage(parseMatchingState(state),member,now).used).toBe(1);});
});
