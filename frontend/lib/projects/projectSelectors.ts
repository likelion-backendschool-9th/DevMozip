import { recruitmentProjects, seedProjectMembers } from '@/mocks/project';
import { collaborations } from '@/mocks/profile';
import type { Collaboration } from '@/types/profile';
import type { RecruitmentProject, RecruitmentState } from '@/types/project';
export function baseProjects(state:RecruitmentState){return [...state.created,...recruitmentProjects].map(p=>state.overrides[p.id]||p);}
export function allProjects(state:RecruitmentState){return baseProjects(state).filter(p=>!state.deletedIds.includes(p.id)).map(p=>{const approved=state.applications.filter(a=>a.projectId===p.id&&a.status==='지원 승인');return {...p,members:p.members+approved.length,slots:p.slots.map(s=>({...s,filled:s.filled+approved.filter(a=>a.role===s.role).length}))};});}
export function teamFor(state:RecruitmentState,projectId:string){const p=baseProjects(state).find(p=>p.id===projectId);if(!p)return [];const members=[...(seedProjectMembers[projectId]||collaborations.find(c=>c.projectId===projectId)?.members||[])];if(!members.some(m=>m.userId===p.hostId))members.unshift({userId:p.hostId,role:'주최자'});for(const a of state.applications.filter(a=>a.projectId===projectId&&a.status==='지원 승인'))if(!members.some(m=>m.userId===a.userId))members.push({userId:a.userId,role:a.role});return members;}
export function projectHistory(state:RecruitmentState):Collaboration[]{return allProjects(state).map(p=>({projectId:p.id,start:p.developmentStart,end:p.developmentEnd,status:p.status||'모집 중',members:teamFor(state,p.id),endedAt:state.endedAt[p.id]||collaborations.find(c=>c.projectId===p.id)?.endedAt}));}

export function chatMembersFor(state:RecruitmentState,projectId:string){if(state.deletedIds.includes(projectId)||state.deletedChatIds.includes(projectId)||!baseProjects(state).some(p=>p.id===projectId))return [];return [...new Set(teamFor(state,projectId).map(m=>m.userId))].filter(id=>!(state.chatExclusions?.[projectId]||[]).includes(id));}
