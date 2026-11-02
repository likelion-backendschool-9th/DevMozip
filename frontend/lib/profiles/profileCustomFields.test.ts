import {describe,it,expect} from 'vitest';
import {applyProfileAction,freshProfileState,parseProfileState} from './profileService';
import {demoAccounts} from '@/mocks/auth';
const actor=demoAccounts[0];
describe('프로필 직접 입력',()=>{
 it('사용자 정의 태그와 링크 및 한 줄 소개를 저장하고 복원한다',()=>{const s=freshProfileState();const patch={...s.profiles[actor.id],bio:'새로운 한 줄 소개',stacks:['React','Astro'],interests:['오픈소스'],externalLinks:[{label:' 포트폴리오 ',url:'https://example.com/work'}]};const next=applyProfileAction(s,actor.id,{type:'profile',patch});expect(next.profiles[actor.id]).toMatchObject({...patch,externalLinks:[{label:'포트폴리오',url:'https://example.com/work'}]});expect(parseProfileState(JSON.stringify(next)).profiles[actor.id]).toEqual(next.profiles[actor.id]);});
 it('빈 태그·중복 태그·안전하지 않은 링크를 거절한다',()=>{const s=freshProfileState();const p=s.profiles[actor.id];for(const patch of [{...p,stacks:[' ']},{...p,stacks:['React','react']},{...p,externalLinks:[{label:'링크',url:'javascript:alert(1)'}]},{...p,externalLinks:[{label:'',url:'https://example.com'}]}])expect(()=>applyProfileAction(s,actor.id,{type:'profile',patch})).toThrow();});
 it('이전 데이터와 빈 링크 목록을 지원하고 변조된 링크는 복원하지 않는다',()=>{const s=freshProfileState();expect(parseProfileState(JSON.stringify(s)).profiles[actor.id]).toEqual(s.profiles[actor.id]);const next=applyProfileAction(s,actor.id,{type:'profile',patch:{...s.profiles[actor.id],externalLinks:[]}});expect(next.profiles[actor.id].externalLinks).toEqual([]);next.profiles[actor.id].externalLinks=[{label:'위험',url:'javascript:alert(1)'}];expect(parseProfileState(JSON.stringify(next)).profiles[actor.id].externalLinks).toBeUndefined();});
});
