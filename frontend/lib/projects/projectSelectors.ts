import { visibleSeeds } from '@/lib/demo/mockVisibility';
import { recruitmentProjects, seedProjectMembers } from '@/mocks/project';
import { collaborations } from '@/mocks/profile';
import type { Collaboration } from '@/types/profile';
import type { RecruitmentProject, RecruitmentState } from '@/types/project';
export function baseProjects(state:RecruitmentState){return [...state.created,...visibleSeeds(recruitmentProjects)].map(p=>state.overrides[p.id]||p);}
export function allProjects(state:RecruitmentState){return baseProjects(state).filter(p=>!state.deletedIds.includes(p.id)).map(p=>{const approved=state.applications.filter(a=>a.projectId===p.id&&a.status==='지원 승인');const removed=historicalTeamFor(state,p.id).filter(m=>kickedFrom(state,p.id,m.userId));return {...p,members:Math.max(1,p.members+approved.length-removed.length),slots:p.slots.map(s=>({...s,filled:Math.max(0,s.filled+approved.filter(a=>a.role===s.role).length-removed.filter(m=>m.role===s.role).length)}))};});}
export function historicalTeamFor(state:RecruitmentState,projectId:string){const p=baseProjects(state).find(p=>p.id===projectId);if(!p)return [];const members=[...((visibleSeeds(seedProjectMembers[projectId]||[]).length?seedProjectMembers[projectId]:undefined)||visibleSeeds(collaborations).find(c=>c.projectId===projectId)?.members||[])];if(!members.some(m=>m.userId===p.hostId))members.unshift({userId:p.hostId,role:'주최자'});for(const a of state.applications.filter(a=>a.projectId===projectId&&a.status==='지원 승인'))if(!members.some(m=>m.userId===a.userId))members.push({userId:a.userId,role:a.role});return members;}
export function projectHistory(state:RecruitmentState):Collaboration[]{return allProjects(state).map(p=>({projectId:p.id,start:p.developmentStart,end:p.developmentEnd,status:p.status||'모집 중',members:teamFor(state,p.id),endedAt:state.endedAt[p.id]||collaborations.find(c=>c.projectId===p.id)?.endedAt}));}

export function chatMembersFor(state:RecruitmentState,projectId:string){if(state.deletedIds.includes(projectId)||state.deletedChatIds.includes(projectId)||!baseProjects(state).some(p=>p.id===projectId))return [];return [...new Set(teamFor(state,projectId).map(m=>m.userId))].filter(id=>!(state.chatExclusions?.[projectId]||[]).includes(id));}

export function kickedFrom(state:RecruitmentState,projectId:string,userId:string){return state.kickVotes?.some(v=>v.projectId===projectId&&v.targetId===userId&&v.status==='passed')??false;}
export function teamFor(state:RecruitmentState,projectId:string){return historicalTeamFor(state,projectId).filter(m=>!kickedFrom(state,projectId,m.userId));}
