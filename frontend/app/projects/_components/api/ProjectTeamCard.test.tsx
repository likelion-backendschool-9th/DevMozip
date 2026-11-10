import {describe,it,expect} from 'vitest';
import {renderToStaticMarkup} from 'react-dom/server';
import {ProjectTeamCard} from './ProjectTeamCard';
describe('프로젝트 팀원 카드',()=>{
 it('왕관을 표시하고 주최자 아래 팀원을 프로필로 연결한다',()=>{
  const html=renderToStaticMarkup(<ProjectTeamCard host={{id:1,tag:'host',nickname:'주최'}} hostMemberId={1} team={[{userId:1,tag:'host',nickname:'주최',role:'HOST',jobRole:null},{userId:2,tag:'member',nickname:'팀원명',role:'PARTICIPANT',jobRole:'BACKEND'}]}/>);
  expect(html).toContain('aria-label="주최자"');expect(html).toContain('href="/users/host"');expect(html).toContain('href="/users/member"');expect(html.indexOf('/users/host')).toBeLessThan(html.indexOf('/users/member'));expect(html.match(/href="\/users\/host"/g)).toHaveLength(1);
 });
 it('팀원이 없는 경우와 구버전 응답을 구분한다',()=>{
  expect(renderToStaticMarkup(<ProjectTeamCard host={null} hostMemberId={1} team={[]}/>)).toContain('아직 참여 팀원이 없습니다.');
  expect(renderToStaticMarkup(<ProjectTeamCard host={null} hostMemberId={1} team={undefined}/>)).toContain('팀원 정보를 아직 불러오지 못했습니다.');
 });
});
