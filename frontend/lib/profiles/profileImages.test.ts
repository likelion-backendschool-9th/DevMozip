import {describe,it,expect} from 'vitest';
import {applyProfileAction,freshProfileState,parseProfileState} from './profileService';
const image='data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jVZkAAAAASUVORK5CYII=';
describe('프로필 이미지 변경',()=>{
 it('프로필과 배너 이미지를 저장·복원·삭제한다',()=>{const s=freshProfileState();const p=s.profiles['demo-user'];const next=applyProfileAction(s,p.id,{type:'profile',patch:{...p,avatar:image,banner:image}});expect(parseProfileState(JSON.stringify(next)).profiles[p.id]).toMatchObject({avatar:image,banner:image});expect(applyProfileAction(next,p.id,{type:'profile',patch:{...next.profiles[p.id],avatar:'',banner:''}}).profiles[p.id]).toMatchObject({avatar:'',banner:''});});
 it('임의 URL·SVG·너무 큰 이미지를 거절한다',()=>{const s=freshProfileState();const p=s.profiles['demo-user'];for(const banner of ['javascript:alert(1)','https://example.com/photo.png','data:image/svg+xml;base64,AAAA','data:image/png;base64,'+'a'.repeat(400000)])expect(()=>applyProfileAction(s,p.id,{type:'profile',patch:{...p,banner}})).toThrow();});
 it('기존 데이터는 호환하고 변조된 배너는 복원하지 않는다',()=>{const s=freshProfileState();expect(parseProfileState(JSON.stringify(s)).profiles['demo-user']).toEqual(s.profiles['demo-user']);s.profiles['demo-user'].banner='javascript:alert(1)';expect(parseProfileState(JSON.stringify(s)).profiles['demo-user'].banner).toBeUndefined();});
});
