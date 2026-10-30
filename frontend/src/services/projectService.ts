import {validKickVotes} from './chatKickService';
import {canCreateProject} from './projectPermissionsService';
export {canCreateProject} from './projectPermissionsService';
import {validReviewRequests} from './projectReviewService';
import { validateProjectInput } from './projectValidation';
export { validateProjectInput } from './projectValidation';
import { allProjects, teamFor } from './projectSelectors';
import { addNotice, manageProject, syncProjectState } from './projectManagementService';
import type { ManagementAction } from '../types/project';
import { cancellationPolicy, initialRecruitmentState, projectFields, recruitmentProjects, recruitmentRoles, reportReasons } from '../mocks/project';
import { stackOptions } from '../mocks/home';
import { collaborations } from '../mocks/profile';
import type { AuthUser } from '../types/auth';
import type { ProfileState } from '../types/profile';
import type { CreateProjectInput, ProjectListFilters, RecruitmentAction, RecruitmentProject, RecruitmentState } from '../types/project';
import { createMockId } from '../utils/createMockId';
export const PROJECT_KEY='devmozip:recruitment:v1';
export function freshRecruitmentState():RecruitmentState{return structuredClone(initialRecruitmentState);}
export { allProjects } from './projectSelectors';
export function projectStatus(project:Pick<RecruitmentProject,'status'|'deadline'>,now=new Date()){return !project.status||project.status==='모집 중'?(now.getTime()>new Date(`${project.deadline}T23:59:59.999+09:00`).getTime()?'모집 마감':'모집 중'):project.status;}
export function isLateCancellation(project:RecruitmentProject,now=new Date()){const cutoff=new Date(`${project.deadline}T00:00:00+09:00`).getTime()-cancellationPolicy.days*86400000;return now.getTime()>=cutoff;}
export function applicationFor(state:RecruitmentState,projectId:string,userId?:string){return state.applications.find(a=>a.projectId===projectId&&a.userId===userId);}
function careerYears(value:string){return /미만|신입|학습/.test(value)?0:Number(value.match(/\d+/)?.[0]||0);}
export function applicationRestriction(project:RecruitmentProject,user:AuthUser|null,now=new Date(),state?:RecruitmentState){
 if(!user)return '로그인 후 프로젝트에 지원할 수 있습니다.';
 if(project.hostId===user.id)return '주최자는 본인 프로젝트에 지원할 수 없습니다.';
 if(teamFor(state||freshRecruitmentState(),project.id).some(m=>m.userId===user.id))return '이미 이 프로젝트에 참여하고 있습니다.';
 if(projectStatus(project,now)!=='모집 중')return '현재 지원을 받지 않는 프로젝트입니다.';
 if(now.getTime()<new Date(`${project.recruitmentStart}T00:00:00+09:00`).getTime())return '아직 모집 시작 전입니다.';
 if(!project.slots.some(s=>s.filled<s.count))return '모집 인원이 모두 충원되었습니다.';
 return '';
}
function applyBaseAction(state:RecruitmentState,action:Exclude<RecruitmentAction,ManagementAction>,user:AuthUser|null,profiles:ProfileState,now=new Date()):{state:RecruitmentState;message:string;projectId?:string}{
 if(!user)throw new Error('로그인 후 이용해 주세요.');
 const next=structuredClone(state);
 if(action.type==='create'){
 if(!canCreateProject(user))throw new Error('프로젝트 모집 공고 생성은 프리미엄 전용입니다.');
 const errors=validateProjectInput(action.input,now);if(errors.length)throw new Error(errors[0]);
 const p=action.input;const id=createMockId();const project:RecruitmentProject={...p,id,title:p.title.trim(),summary:p.summary.trim(),hostId:user.id,roles:p.slots.map(s=>s.role),slots:p.slots.map(s=>({...s,filled:0})),members:1,capacity:1+p.slots.reduce((sum,s)=>sum+s.count,0),duration:`${p.developmentStart} — ${p.developmentEnd}`,createdAt:now.toISOString().slice(0,10),views:0,color:'lavender',mark:p.title.trim().slice(0,1).toUpperCase(),status:'모집 중'};
 next.created.unshift(project);return {state:next,projectId:id,message:'Mock 모집 공고를 등록했습니다.'};
 }
 const project=allProjects(next).find(p=>p.id===action.projectId);if(!project)throw new Error('프로젝트를 찾을 수 없습니다.');
 if(action.type==='bookmark'){const current=next.bookmarks[user.id]||[];const existing=current.includes(project.id);next.bookmarks[user.id]=existing?current.filter(id=>id!==project.id):[...current,project.id];return {state:next,message:existing?'북마크를 해제했습니다.':'프로젝트를 북마크했습니다.'};}
 if(action.type==='report'){if(project.hostId===user.id)throw new Error('본인 공고는 신고할 수 없습니다.');if(next.reports.some(r=>r.projectId===project.id&&r.userId===user.id))throw new Error('이미 신고한 프로젝트입니다.');if(!reportReasons.includes(action.reason)||action.detail.length>1000)throw new Error('신고 사유와 내용을 확인해 주세요.');next.reports.push({id:createMockId(),projectId:project.id,userId:user.id,reason:action.reason,detail:action.detail,createdAt:now.toISOString()});return {state:next,message:'Mock 신고를 기록했습니다. 실제 서버에는 전송하지 않습니다.'};}
 const application=applicationFor(next,project.id,user.id);
 if(action.type==='cancel'){if(!application||!['지원 완료','지원 승인'].includes(application.status))throw new Error('취소할 지원 내역이 없습니다.');if(project.status==='프로젝트 종료')throw new Error('종료된 프로젝트의 지원은 취소할 수 없습니다.');if(isLateCancellation(project,now)&&!action.acknowledgePenalty)throw new Error('마감 임박 취소에 대한 임시 패널티 안내를 확인해 주세요.');application.status='지원 취소';application.cancelledAt=now.toISOString();return {state:next,message:'지원을 취소했습니다. 신뢰 온도는 변경하지 않았습니다.'};}
 const restriction=applicationRestriction(project,user,now,next);if(restriction)throw new Error(restriction);
 if(application&&['지원 완료','지원 승인'].includes(application.status))throw new Error('이미 지원한 프로젝트입니다.');
 const portfolio=profiles.portfolios.find(p=>p.id===action.portfolioId&&p.ownerId===user.id);if(!portfolio)throw new Error('본인의 포트폴리오를 선택해 주세요.');
 const slot=project.slots.find(s=>s.role===action.role);if(!slot||slot.filled>=slot.count)throw new Error('지원 가능한 모집 직군을 선택해 주세요.');
 const career=profiles.profiles[user.id]?.career||user.career;if(careerYears(career)<slot.minYears)throw new Error(`해당 직군은 최소 ${slot.minYears}년의 경력이 필요합니다.`);
 if(action.message.trim().length<10||action.message.length>1500)throw new Error('지원 멘트는 10~1,500자로 입력해 주세요.');
 const record={id:application?.id||createMockId(),projectId:project.id,userId:user.id,role:action.role,portfolioId:portfolio.id,portfolioTitle:portfolio.title,message:action.message.trim(),status:'지원 완료' as const,createdAt:now.toISOString()};next.applications=next.applications.filter(a=>a.id!==application?.id);next.applications.unshift(record);return {state:next,message:'프로젝트 지원을 완료했습니다.'};
}
export function applyRecruitmentAction(state:RecruitmentState,action:RecruitmentAction,user:AuthUser|null,profiles:ProfileState,now=new Date()){
 const result=['edit','delete','decide','status','extend'].includes(action.type)?manageProject(state,action as ManagementAction,user,now):applyBaseAction(state,action as Exclude<RecruitmentAction,ManagementAction>,user,profiles,now);
 if(user&&action.type==='apply')addNotice(result.state,allProjects(result.state).find(p=>p.id===action.projectId)!.hostId,action.projectId,'프로젝트 지원 도착',`${user.nickname}님이 지원했습니다.`,now);
 if(user&&action.type==='cancel')addNotice(result.state,allProjects(result.state).find(p=>p.id===action.projectId)!.hostId,action.projectId,'프로젝트 지원 취소',`${user.nickname}님이 지원을 취소했습니다. 팀원·채팅 준비 상태를 반영했습니다.`,now);
 return {...result,state:syncProjectState(result.state,now,profiles)};
}
export function parseProjectListFilters(params:URLSearchParams):ProjectListFilters{return {q:(params.get('q')||'').slice(0,100),role:recruitmentRoles.includes(params.get('role')||'')?params.get('role')!:'',field:projectFields.includes(params.get('field')||'')?params.get('field')!:'',status:['모집 중','모집 마감','모집 완료','프로젝트 진행 중','프로젝트 종료'].includes(params.get('status')||'')?params.get('status')!:'',stack:stackOptions.includes(params.get('stack')||'')?params.get('stack')!:'',sort:['popular','deadline'].includes(params.get('sort')||'')?params.get('sort') as 'popular'|'deadline':'latest'};}
export function filterProjectList(projects:RecruitmentProject[],f:ProjectListFilters,now=new Date()){const terms=f.q.normalize('NFKC').toLowerCase().trim().split(/\s+/).filter(Boolean);return projects.filter(p=>terms.every(t=>`${p.title} ${p.stacks.join(' ')}`.normalize('NFKC').toLowerCase().includes(t))&&(!f.role||p.roles.includes(f.role))&&(!f.field||p.field===f.field)&&(!f.stack||p.stacks.includes(f.stack))&&(!f.status||projectStatus(p,now)===f.status)).sort((a,b)=>f.sort==='popular'?b.views-a.views||b.createdAt.localeCompare(a.createdAt):f.sort==='deadline'?Number(projectStatus(b,now)==='모집 중')-Number(projectStatus(a,now)==='모집 중')||a.deadline.localeCompare(b.deadline)||b.createdAt.localeCompare(a.createdAt):b.createdAt.localeCompare(a.createdAt));}
export function parseRecruitmentState(raw:string|null):RecruitmentState{
 try{const v=JSON.parse(raw||'null');if(v?.version!==1||!Array.isArray(v.created)||!Array.isArray(v.applications)||!v.bookmarks||typeof v.bookmarks!=='object'||Array.isArray(v.bookmarks)||!Array.isArray(v.reports))return freshRecruitmentState();
 const text=(p:any,keys:string[])=>p&&keys.every(k=>typeof p[k]==='string');const strings=(v:any)=>Array.isArray(v)&&v.every(s=>typeof s==='string');
 if(!v.created.every((p:any)=>text(p,['id','title','summary','description','goal','hostId','developmentStart','developmentEnd','recruitmentStart','deadline','field','duration','createdAt','color','mark'])&&strings(p.roles)&&strings(p.stacks)&&typeof p.members==='number'&&typeof p.capacity==='number'&&typeof p.views==='number'&&p.status==='모집 중'&&Array.isArray(p.slots)&&p.slots.every((s:any)=>text(s,['role'])&&Number.isInteger(s.count)&&s.count>0&&s.count<=10&&s.filled===0&&Number.isInteger(s.minYears)&&s.minYears>=0&&s.minYears<=5)&&validateProjectInput(p,new Date('1970-01-01')).length===0))return freshRecruitmentState();
 const projectIds=[...recruitmentProjects,...v.created].map(p=>p.id);if(new Set(projectIds).size!==projectIds.length)return freshRecruitmentState();
 if(!v.applications.every((a:any)=>text(a,['id','projectId','userId','role','portfolioId','portfolioTitle','message','createdAt'])&&projectIds.includes(a.projectId)&&['지원 완료','지원 취소','지원 승인','지원 거절'].includes(a.status)))return freshRecruitmentState();
 if(new Set(v.applications.map((a:any)=>`${a.projectId}:${a.userId}`)).size!==v.applications.length)return freshRecruitmentState();
 if(!Object.values(v.bookmarks).every(a=>strings(a)&&(a as string[]).every(id=>projectIds.includes(id))))return freshRecruitmentState();
 if(!v.reports.every((a:any)=>text(a,['id','projectId','userId','reason','detail','createdAt'])&&projectIds.includes(a.projectId)&&reportReasons.includes(a.reason)))return freshRecruitmentState();
 const defaults=freshRecruitmentState();
 if(v.kickVotes!==undefined&&(!validKickVotes(v.kickVotes)||v.kickVotes.some((vote:any)=>!projectIds.includes(vote.projectId))))return defaults;
 if(v.chatExclusions!==undefined&&(!v.chatExclusions||typeof v.chatExclusions!=='object'||Array.isArray(v.chatExclusions)||!Object.entries(v.chatExclusions).every(([id,ids])=>projectIds.includes(id)&&strings(ids)&&new Set(ids as string[]).size===(ids as string[]).length)))return defaults;
 if(v.reviewRequests!==undefined&&(!validReviewRequests(v.reviewRequests)||v.reviewRequests.some((r:any)=>!projectIds.includes(r.projectId))))return defaults;
 if(v.simulationDate!==undefined&&(typeof v.simulationDate!=='string'||!Number.isFinite(Date.parse(v.simulationDate))))return defaults;
 if(v.deletedChatIds!==undefined&&(!strings(v.deletedChatIds)||v.deletedChatIds.some((id:string)=>!projectIds.includes(id))))return defaults;
 for(const key of ['overrides','endedAt','chatMembers'])if(v[key]!==undefined&&(!v[key]||typeof v[key]!=='object'||Array.isArray(v[key])))return defaults;
 if(v.deletedIds!==undefined&&(!strings(v.deletedIds)||v.deletedIds.some((id:string)=>!projectIds.includes(id))))return defaults;
 if(v.notifications!==undefined&&(!Array.isArray(v.notifications)||!v.notifications.every((n:any)=>text(n,['id','recipientId','projectId','title','body','createdAt'])&&['href','resolvedAt','scheduledEnd','reviewRequestId'].every(k=>n[k]===undefined||typeof n[k]==='string')&&(n.kind===undefined||['development-overdue','review-request','review-reminder','chat-kick'].includes(n.kind))&&(n.actions===undefined||Array.isArray(n.actions)&&n.actions.every((a:any)=>text(a,['label','href'])&&a.href.startsWith('/'))))))return defaults;
 for(const [id,p] of Object.entries(v.overrides||{}) as [string,any][]){if(!projectIds.includes(id)||p?.id!==id||!text(p,['hostId','title','summary','description','goal','developmentStart','developmentEnd','recruitmentStart','deadline','field','duration','createdAt','color','mark'])||!strings(p.roles)||!strings(p.stacks)||!['모집 중','모집 완료','프로젝트 진행 중','프로젝트 종료'].includes(p.status||'모집 중')||!Array.isArray(p.slots)||p.slots.some((s:any)=>!text(s,['role'])||!Number.isInteger(s.filled)||s.filled<0||s.filled>s.count)||validateProjectInput(p,new Date('1970-01-01')).length)return defaults;}
 if(!Object.values(v.chatMembers||{}).every(strings)||!Object.values(v.endedAt||{}).every(t=>typeof t==='string'&&!Number.isNaN(Date.parse(t))))return defaults;
 return {...defaults,...v};}catch{return freshRecruitmentState();}
}
export function loadRecruitmentState(){try{return parseRecruitmentState(localStorage.getItem(PROJECT_KEY));}catch{return freshRecruitmentState();}}
export function saveRecruitmentState(state:RecruitmentState){try{localStorage.setItem(PROJECT_KEY,JSON.stringify(state));return true;}catch{return false;}}
