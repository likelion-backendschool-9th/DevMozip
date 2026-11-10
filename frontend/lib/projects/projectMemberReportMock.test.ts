import {expect,it} from 'vitest';
import {applyMessagingAction,freshMessagingState,parseMessagingState} from '@/lib/messaging/messagingService';
import {freshRecruitmentState} from '@/lib/projects/projectService';
import {freshProfileState} from '@/lib/profiles/profileService';
import {demoAccounts} from '@/mocks/auth';
const [,host]=demoAccounts;const projects=freshRecruitmentState();const profiles=freshProfileState();const now=new Date('2026-11-01T12:00:00+09:00');
it('일반 사용자 신고와 프로젝트 팀원 신고를 구분하고 새로고침 후 맥락을 보존한다',()=>{
 let state=applyMessagingAction(freshMessagingState(),{type:'report-user',targetId:'yuna',reason:'기타'},host,profiles,projects,now).state;
 state=applyMessagingAction(state,{type:'report-user',targetId:'yuna',reason:'기타',projectId:'pocket'},host,profiles,projects,now).state;
 expect(state.userReports.filter(r=>r.targetId==='yuna')).toHaveLength(2);
 expect(()=>applyMessagingAction(state,{type:'report-user',targetId:'yuna',reason:'기타',projectId:'pocket'},host,profiles,projects,now)).toThrow('이미 신고');
 expect(parseMessagingState(JSON.stringify(state)).userReports.some(r=>r.projectId==='pocket')).toBe(true);
});
