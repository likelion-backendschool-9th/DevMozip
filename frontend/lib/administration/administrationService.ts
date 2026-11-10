import { mockDataEnabled, visibleSeeds, readDemoStorage, writeDemoStorage } from '@/lib/demo/mockVisibility';
import type { AuthUser } from '@/types/auth';
import type { AdministrationAction,AdministrationState } from '@/types/administration';
import { initialAdministration } from '@/mocks/administration';
import { createMockId } from '@/lib/utils/createMockId';
export const ADMINISTRATION_KEY='devmozip:administration:v1';
export const freshAdministration=():AdministrationState=>({...structuredClone(initialAdministration),payments:mockDataEnabled()?structuredClone(initialAdministration.payments):[]});
export function effectiveUser(user:AuthUser,state:AdministrationState):AuthUser {return {...user,role:user.role==='admin'?'admin':state.subscriptions[user.id]?state.subscriptions[user.id].active?'premium':'free':user.role};}
export const isSuspended=(state:AdministrationState,id:string)=>state.members[id]?.status==='suspended';
export function applyAdministration(state:AdministrationState,actor:AuthUser|null,action:AdministrationAction,knownIds:string[],now=new Date()):AdministrationState {
 if(!actor||isSuspended(state,actor.id))throw new Error('로그인 후 이용해 주세요.');
 const next=structuredClone(state);const date=now.toISOString();
 if(action.type==='subscribe'||action.type==='unsubscribe'){
  if(actor.role==='admin')throw new Error('관리자 계정은 구독 전환 대상이 아닙니다.');
  const active=effectiveUser(actor,state).role==='premium';if(active===(action.type==='subscribe'))throw new Error('이미 해당 구독 상태입니다.');
  next.subscriptions[actor.id]={active:action.type==='subscribe',updatedAt:date};
  if(action.type==='subscribe')next.payments.unshift({id:createMockId(),userId:actor.id,date:date.slice(0,10),amount:0,status:'데모 전환 · 실제 청구 없음'});
 }else{
  if(actor.role!=='admin')throw new Error('관리자만 변경할 수 있습니다.');
  if(action.type==='member'){if(!knownIds.includes(action.targetId)||actor.id===action.targetId)throw new Error('관리할 회원을 확인해 주세요.');if(action.status!=='active'&&!action.reason.trim())throw new Error('경고·정지 사유를 입력해 주세요.');if(action.reason.length>300)throw new Error('사유는 300자 이내입니다.');next.members[action.targetId]={status:action.status,reason:action.reason.trim(),updatedAt:date};}
  if(action.type==='resolve'){if(!action.reportId||!action.note.trim()||action.note.length>300)throw new Error('처리 메모를 1~300자로 입력해 주세요.');next.resolutions[action.reportId]={status:action.status,note:action.note.trim(),updatedAt:date};}
  if(action.type==='keyword-add'){const k=action.keyword.trim();if(!k||k.length>30||next.keywords.includes(k))throw new Error('키워드는 중복 없이 1~30자로 입력해 주세요.');next.keywords.push(k);}
  if(action.type==='keyword-remove'){if(!next.keywords.includes(action.keyword))throw new Error('키워드를 찾을 수 없습니다.');next.keywords=next.keywords.filter(k=>k!==action.keyword);}
 }
 next.audit.unshift({id:createMockId(),actorId:actor.id,action:action.type,createdAt:date});return next;
}
export function parseAdministration(raw:string|null):AdministrationState {try{const s=JSON.parse(raw||'null');const record=(v:any)=>v&&typeof v==='object'&&!Array.isArray(v);if(s?.version!==1||!record(s.subscriptions)||!Object.values(s.subscriptions).every((v:any)=>v&&typeof v.active==='boolean'&&typeof v.updatedAt==='string')||!record(s.members)||!Object.values(s.members).every((v:any)=>v&&['active','warned','suspended'].includes(v.status)&&typeof v.reason==='string'&&typeof v.updatedAt==='string')||!record(s.resolutions)||!Object.values(s.resolutions).every((v:any)=>v&&['resolved','dismissed'].includes(v.status)&&typeof v.note==='string'&&typeof v.updatedAt==='string')||!Array.isArray(s.keywords)||s.keywords.some((v:any)=>typeof v!=='string'||!v.trim()||v.length>30)||new Set(s.keywords).size!==s.keywords.length||!Array.isArray(s.audit)||!s.audit.every((v:any)=>v&&['id','actorId','action','createdAt'].every(k=>typeof v[k]==='string'))||!Array.isArray(s.payments)||!s.payments.every((v:any)=>v&&['id','userId','date','status'].every(k=>typeof v[k]==='string')&&Number.isFinite(v.amount)&&v.amount>=0))return freshAdministration();return s;}catch{return freshAdministration();}}
export function loadAdministration(){try{return parseAdministration(readDemoStorage(ADMINISTRATION_KEY));}catch{return freshAdministration();}}
export function saveAdministration(state:AdministrationState){try{writeDemoStorage(ADMINISTRATION_KEY,JSON.stringify(state));return true;}catch{return false;}}
