import {describe,it,expect} from 'vitest';
import {applyProfileAction,freshProfileState,parseProfileState} from './profileService';
describe('프로필 닉네임·태그 변경',()=>{
 it('계정 ID와 다른 정보는 유지하며 변경·복원한다',()=>{const s=freshProfileState();const p=s.profiles['demo-user'];const next=applyProfileAction(s,p.id,{type:'profile',patch:{...p,nickname:' 새이름 ',handle:' new_handle '}});expect(next.profiles[p.id]).toMatchObject({id:p.id,nickname:'새이름',handle:'new_handle',introduction:p.introduction});expect(parseProfileState(JSON.stringify(next)).profiles[p.id]).toEqual(next.profiles[p.id]);});
 it('중복 닉네임·태그와 잘못된 입력을 거절한다',()=>{const s=freshProfileState();const p=s.profiles['demo-user'];const other=s.profiles['demo-premium'];for(const patch of [{nickname:other.nickname},{handle:other.handle.toUpperCase()},{nickname:'x'},{handle:'@test'},{handle:'공백 태그'}])expect(()=>applyProfileAction(s,p.id,{type:'profile',patch:{...p,...patch}})).toThrow();});
 it('동일한 자신의 정보와 기존 호출을 허용한다',()=>{const s=freshProfileState();const p=s.profiles['demo-user'];expect(applyProfileAction(s,p.id,{type:'profile',patch:{...p}}).profiles[p.id].nickname).toBe(p.nickname);});
});
