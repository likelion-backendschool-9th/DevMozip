import { parseMatchingChat } from '@/lib/matching/matchingChatService';
import { buildMatchingResult,parseMatchingState } from '@/lib/matching/matchingService';
import { projectStatus } from '@/lib/projects/projectService';
import { defaultHomeState, developers, posts, projects, stackOptions, interestOptions, interestFilterOptions } from '@/mocks/home';
import { jobOptions } from '@/mocks/auth';
import type { HomeState, ProjectSort, RecommendationInput, Post } from '@/types/home';
export const HOME_KEY = 'devmozip:home:v1';
export function freshHomeState(): HomeState { return structuredClone(defaultHomeState); }
export function parseHomeState(raw: string | null): HomeState {
 try { const v = JSON.parse(raw || 'null'); if(v?.version===1 && typeof v.input?.description==='string' && Array.isArray(v.input.stacks) && v.input.stacks.every((s: unknown) => typeof s==='string' && stackOptions.includes(s)) && interestFilterOptions.includes(v.input.interest)) return {version:1,...(typeof v.inputOwner==='string'?{inputOwner:v.inputOwner}:{}),chat:migrateMatchingChat(v.chat,v.matching),matching:parseMatchingState(v.matching),input:{description:v.input.description.slice(0,1500),stacks:v.input.stacks,interest:v.input.interest,role:jobOptions.includes(v.input.role)?v.input.role:'',skipStacks:v.input.skipStacks===true,skipInterest:v.input.skipInterest===true,skipRole:v.input.skipRole===true}}; } catch { /* Restore defaults for invalid storage. */ }
 return freshHomeState();
}
export function loadHomeState() { try { return parseHomeState(localStorage.getItem(HOME_KEY)); } catch { return freshHomeState(); } }
export function saveHomeState(state: HomeState) { try { localStorage.setItem(HOME_KEY,JSON.stringify(state)); return true; } catch { return false; } }
export function recommend(input: RecommendationInput, candidates = developers, projectCandidates = projects) {
 return buildMatchingResult(input,candidates,projectCandidates);
}
export function sortProjects(sort: ProjectSort, source = projects) { return source.filter(p=>projectStatus(p)==='모집 중').sort((a,b)=>sort==='popular'?b.views-a.views:sort==='deadline'?a.deadline.localeCompare(b.deadline):b.createdAt.localeCompare(a.createdAt)); }
export function filterPosts<T extends Post>(mode:string,category:string,following:string[],signedIn:boolean,source:T[]):T[];
export function filterPosts(mode:string,category:string,following:string[],signedIn:boolean):Post[];
export function filterPosts(mode: string, category: string, following: string[], signedIn: boolean, source:Post[] = posts) { return source.filter(p=>(category==='전체'||p.category===category)&&(mode!=='following'||(signedIn&&following.includes(p.authorId)))); }

function migrateMatchingChat(raw:unknown,matchingRaw:unknown){const chat=parseMatchingChat(raw);const matching=parseMatchingState(matchingRaw);for(const [owner,snapshot] of Object.entries(matching.last)){if(raw&&typeof raw==='object'||chat.conversations[owner]?.length)continue;try{const input=JSON.parse(snapshot.inputKey);if(typeof input.description!=='string'||!Number.isFinite(Date.parse(snapshot.createdAt)))continue;const requirements={description:input.description,fields:[],interests:snapshot.result.analysis.interests,stacks:snapshot.result.analysis.stacks,roles:snapshot.result.analysis.role?[{name:snapshot.result.analysis.role,count:1}]:[],career:'',duration:''};const id='legacy-'+owner;chat.conversations[owner]=[{id,title:input.description.slice(0,32)||'이전 추천',createdAt:snapshot.createdAt,updatedAt:snapshot.createdAt,requirements,messages:[{id:id+'-input',role:'user',text:input.description,createdAt:snapshot.createdAt},{id:id+'-result',role:'assistant',text:'이전 추천 결과를 복원했습니다. 새 추천에는 추가 프로젝트 조건을 설정해 주세요.',createdAt:snapshot.createdAt,result:snapshot.result}]}];chat.active[owner]=id;}catch{/* Preserve usage even if a legacy snapshot cannot be migrated. */}}return chat;}
