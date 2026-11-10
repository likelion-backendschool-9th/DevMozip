import type { ProjectRecord, ProjectPageResult, ProjectDetailResult, ProjectStatusCode, ProjectTeamMember } from '@/types/projectApi';
export const statusLabels:Record<ProjectStatusCode,string>={RECRUITING:'모집 중',RECRUITMENT_COMPLETED:'모집 완료',IN_PROGRESS:'프로젝트 진행 중',COMPLETED:'프로젝트 종료'};
export const roleLabels:Record<string,string>={FRONTEND:'프론트엔드',BACKEND:'백엔드',FULLSTACK:'풀스택',DESIGNER:'UI/UX',DEVOPS:'DevOps',DATA_ENGINEER:'데이터 엔지니어',AI_ENGINEER:'AI 엔지니어',MOBILE:'모바일',GAME:'게임',OTHER:'기타'};
export const projectFields=['title','summary','description','goal','imageUrl','isPublic','recruitmentSlots','techStacks','interests','recruitmentStart','recruitmentEnd','developmentStart','developmentEnd'] as const;
export function pickProjectRequest(value:unknown){
 if(!value||typeof value!=='object'||Array.isArray(value))throw new Error('JSON 객체가 필요합니다.');
 const data=value as Record<string,unknown>;
 return Object.fromEntries(projectFields.map(key=>[key,data[key]]));
}
export function validProjectId(id:string){return /^[1-9]\d*$/.test(id)&&Number.isSafeInteger(Number(id));}
const record=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v);
export function parseProject(value:unknown):ProjectRecord{
 if(!record(value)||!Number.isSafeInteger(value.id)||Number(value.id)<=0||typeof value.title!=='string'||typeof value.hostMemberId!=='number'||typeof value.isPublic!=='boolean'||typeof value.status!=='string'||!Object.hasOwn(statusLabels,value.status)||!Array.isArray(value.recruitmentSlots)||!Array.isArray(value.techStacks)||!value.techStacks.every(x=>typeof x==='string')||!Array.isArray(value.interests)||!value.interests.every(x=>typeof x==='string'))throw new Error('잘못된 프로젝트 응답');
 for(const key of ['imageUrl','recruitmentStart','recruitmentEnd','developmentStart','developmentEnd','createdAt'])if(typeof value[key]!=='string')throw new Error('잘못된 프로젝트 응답');
 if(typeof value.totalCapacity!=='number'||typeof value.viewCount!=='number'||!value.recruitmentSlots.every(s=>record(s)&&typeof s.roleCode==='string'&&typeof s.capacity==='number'&&typeof s.minimumYears==='number'))throw new Error('잘못된 프로젝트 응답');
 return {...pickProjectRequest({...value,description:value.description??'',goal:value.goal??null}),id:value.id,hostMemberId:value.hostMemberId,status:value.status,totalCapacity:value.totalCapacity,viewCount:value.viewCount,createdAt:value.createdAt} as ProjectRecord;
}
export function parseProjectPage(value:unknown):ProjectPageResult{
 if(!record(value)||!Array.isArray(value.items)||!['totalElements','page','size','totalPages'].every(k=>typeof value[k]==='number'&&Number.isInteger(value[k])&&Number(value[k])>=0))throw new Error('잘못된 목록 응답');
 return {items:value.items.map(item=>{const p=parseProject(item);if(!record(item)||typeof item.memberCount!=='number'||typeof item.recruitmentClosed!=='boolean'||![null,'HOST','PARTICIPANT'].includes(item.myRole as string|null))throw new Error('잘못된 목록 응답');return {...p,memberCount:item.memberCount,recruitmentClosed:item.recruitmentClosed,myRole:item.myRole as 'HOST'|'PARTICIPANT'|null};}),totalElements:value.totalElements as number,page:value.page as number,size:value.size as number,totalPages:value.totalPages as number};
}
export function parseProjectTeam(value:unknown):ProjectTeamMember[]{
 if(!Array.isArray(value))throw new Error('잘못된 팀원 응답');
 const team=value.map(member=>{
  if(!record(member)||!Number.isSafeInteger(member.userId)||Number(member.userId)<=0||!['HOST','PARTICIPANT'].includes(String(member.role))||![member.tag,member.nickname,member.jobRole].every(v=>v===null||typeof v==='string'))throw new Error('잘못된 팀원 응답');
  return {userId:Number(member.userId),tag:member.tag as string|null,nickname:member.nickname as string|null,role:member.role as ProjectTeamMember['role'],jobRole:member.jobRole as string|null};
 });
 if(new Set(team.map(m=>m.userId)).size!==team.length)throw new Error('중복된 팀원 응답');
 return team;
}
export function parseProjectDetail(value:unknown):ProjectDetailResult{
 if(!record(value)||typeof value.memberCount!=='number'||typeof value.recruitmentClosed!=='boolean')throw new Error('잘못된 상세 응답');
 const m=value.membership;if(m!=null&&(!record(m)||!['HOST','PARTICIPANT'].includes(String(m.myRole))||typeof m.myEndRequested!=='boolean'||typeof m.endRequestCount!=='number'||typeof m.requiredEndRequestCount!=='number'))throw new Error('잘못된 참여 정보');
 return {...(value.team===undefined?{}:{team:parseProjectTeam(value.team)}),project:parseProject(value.project),memberCount:value.memberCount,recruitmentClosed:value.recruitmentClosed,membership:(m??null) as ProjectDetailResult['membership'],host:record(value.host)?{id:Number(value.host.id),tag:typeof value.host.tag==='string'?value.host.tag:null,nickname:typeof value.host.nickname==='string'?value.host.nickname:null}:null};
}
