import {syncKickVotes} from '@/lib/projects/chatKickService';
import { allProjects, baseProjects, teamFor, chatMembersFor } from '@/lib/projects/projectSelectors';
import { validateProjectInput } from '@/lib/projects/projectValidation';
import {addNotice} from '@/lib/projects/projectNoticeService';
export {addNotice} from '@/lib/projects/projectNoticeService';
import {syncReviewRequests} from '@/lib/projects/projectReviewService';
import {syncDevelopmentDeadlines} from '@/lib/projects/projectDeadlineService';
import type {ProfileState} from '@/types/profile';
import type { AuthUser } from '@/types/auth';
import type { ManagementAction, RecruitmentProject, RecruitmentState } from '@/types/project';
export const lifecycleOptions=['모집 중','모집 완료','프로젝트 진행 중','프로젝트 종료'] as const;
export function syncProjectState(state:RecruitmentState,now=new Date(),profiles?:ProfileState){const next=structuredClone(state);for(const p of allProjects(next)){next.chatMembers[p.id]=chatMembersFor(next,p.id);if((p.status||'모집 중')==='모집 중'&&now.getTime()>new Date(`${p.deadline}T23:59:59.999+09:00`).getTime())addNotice(next,'demo-admin',p.id,'모집 기한 경과 확인 필요',`${p.title}: 기한이 지났지만 모집 완료로 변경되지 않았습니다.`,now,`overdue:${p.id}:${p.deadline}`);}syncKickVotes(next,now,profiles);syncDevelopmentDeadlines(next,now);syncReviewRequests(next,now,profiles);return next;}
function related(state:RecruitmentState,p:RecruitmentProject){return [...new Set([...state.applications.filter(a=>a.projectId===p.id).map(a=>a.userId),...teamFor(state,p.id).map(m=>m.userId)])].filter(id=>id!==p.hostId);}
function notifyRelated(state:RecruitmentState,p:RecruitmentProject,title:string,body:string,now:Date){for(const id of related(state,p))addNotice(state,id,p.id,title,body,now);}
export function manageProject(state:RecruitmentState,action:ManagementAction,user:AuthUser|null,now=new Date()):{state:RecruitmentState;message:string;projectId?:string}{
 const next=structuredClone(state);const p=allProjects(next).find(p=>p.id===action.projectId);if(!user||!p||p.hostId!==user.id)throw new Error('프로젝트 주최자만 관리할 수 있습니다.');const base=baseProjects(next).find(p=>p.id===action.projectId)!;
 if(action.type==='delete'){if(next.applications.some(a=>a.projectId===p.id&&['지원 완료','지원 승인'].includes(a.status))||teamFor(next,p.id).some(m=>m.userId!==p.hostId))throw new Error('남아 있는 지원자 또는 참여 팀원이 있어 삭제할 수 없습니다. 대기 지원은 거절하거나 지원자가 취소해야 합니다.');notifyRelated(next,p,'프로젝트 공고 삭제',`${p.title} 공고가 삭제되었습니다.`,now);next.deletedIds.push(p.id);for(const id of Object.keys(next.bookmarks))next.bookmarks[id]=next.bookmarks[id].filter(v=>v!==p.id);delete next.chatMembers[p.id];return {state:next,message:'프로젝트 공고를 삭제했습니다.'};}
 if(p.status==='프로젝트 종료')throw new Error('종료된 프로젝트는 변경할 수 없습니다.');
 if(action.type==='decide'){const a=next.applications.find(a=>a.id===action.applicationId&&a.projectId===p.id);if(!a||a.status!=='지원 완료')throw new Error('검토 대기 중인 지원서만 승인 또는 거절할 수 있습니다.');if(action.decision==='지원 승인'){if(!['모집 중','모집 완료'].includes(p.status||'모집 중'))throw new Error('모집 단계에서만 지원을 승인할 수 있습니다.');const slot=p.slots.find(s=>s.role===a.role);if(!slot||slot.filled>=slot.count)throw new Error('해당 직군의 모집 정원이 찼습니다.');}a.status=action.decision;a.decidedAt=now.toISOString();addNotice(next,a.userId,p.id,action.decision,`${p.title}: ${action.decision==='지원 승인'?'팀원으로 등록되었습니다.':'지원이 거절되었습니다.'}`,now);return {state:next,message:`${action.decision} 처리했습니다.`};}
 if(action.type==='status'){const current=p.status||'모집 중';const allowed:Record<string,readonly string[]>={'모집 중':['모집 완료','프로젝트 종료'],'모집 완료':['모집 중','프로젝트 진행 중','프로젝트 종료'],'프로젝트 진행 중':['프로젝트 종료']};if(!allowed[current]?.includes(action.status))throw new Error('허용되지 않는 프로젝트 상태 전환입니다.');if(['프로젝트 진행 중','프로젝트 종료'].includes(action.status)&&next.applications.some(a=>a.projectId===p.id&&a.status==='지원 완료'))throw new Error('남아 있는 대기 지원서를 먼저 승인 또는 거절해 주세요.');if(action.status==='모집 중'&&new Date(`${p.deadline}T23:59:59.999+09:00`).getTime()<now.getTime())throw new Error('모집 기간을 연장한 뒤 다시 모집을 시작해 주세요.');next.overrides[p.id]={...base,status:action.status};if(action.status==='프로젝트 종료'){next.endedAt[p.id]=now.toISOString();if(action.deleteChatOnEnd&&!next.deletedChatIds.includes(p.id))next.deletedChatIds.push(p.id);for(const member of teamFor(next,p.id)){addNotice(next,member.userId,p.id,'프로젝트 종료',`${p.title} 프로젝트가 종료되었습니다.`,now);}}else notifyRelated(next,p,'프로젝트 상태 변경',`${p.title}: ${action.status}`,now);return {state:next,message:`프로젝트를 ${action.status} 상태로 변경했습니다.`};}
 let input;
 if(action.type==='extend'){if(action.deadline<p.deadline||action.developmentEnd<p.developmentEnd||(action.deadline===p.deadline&&action.developmentEnd===p.developmentEnd))throw new Error('기존 기간보다 긴 날짜를 입력해 주세요.');input={...base,deadline:action.deadline,developmentEnd:action.developmentEnd};}
 else input=action.input;
 const errors=validateProjectInput(input,new Date('1970-01-01'));if(errors.length)throw new Error(errors[0]);
 const active=next.applications.filter(a=>a.projectId===p.id&&['지원 완료','지원 승인'].includes(a.status));
 if(active.some(a=>!input.slots.some(s=>s.role===a.role))||p.slots.some(s=>s.filled>0&&!input.slots.some(n=>n.role===s.role)))throw new Error('지원자 또는 팀원이 있는 직군은 삭제할 수 없습니다.');
 if(input.slots.some(s=>s.count<(p.slots.find(old=>old.role===s.role)?.filled||0)))throw new Error('등록된 팀원 수보다 정원을 줄일 수 없습니다.');
 const capacity=base.capacity+input.slots.reduce((n,s)=>n+s.count,0)-base.slots.reduce((n,s)=>n+s.count,0);
 next.overrides[p.id]={...base,...input,hostId:base.hostId,id:base.id,status:base.status,roles:input.slots.map(s=>s.role),slots:input.slots.map(s=>({...s,filled:base.slots.find(old=>old.role===s.role)?.filled||0})),capacity,duration:`${input.developmentStart} — ${input.developmentEnd}`};
 notifyRelated(next,p,action.type==='extend'?'프로젝트 기간 연장':'프로젝트 공고 수정',`${p.title}: 변경된 모집 조건과 기간을 확인해 주세요.`,now);return {state:next,message:action.type==='extend'?'프로젝트 기간을 연장했습니다.':'프로젝트 공고를 수정했습니다.'};
}
