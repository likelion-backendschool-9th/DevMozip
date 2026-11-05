import { createMockId } from '@/lib/utils/createMockId';
import { chatCareerOptions,chatInterestOptions,chatDomainAliases,chatDomainStacks,chatRoleAliases,emptyChatRequirements,initialMatchingChat } from '@/mocks/matchingChat';
import { stackOptions } from '@/mocks/home';
import { analyzeMatching,buildMatchingResult,matchingUsage,parseMatchingState } from '@/lib/matching/matchingService';
import type { ChatRequirements,MatchingChatState,MatchingConversation,MatchingMessage } from '@/types/matchingChat';
import type { MatchingState,MatchingResult } from '@/types/matching';
import type { Developer,Project } from '@/types/home';
const unique=(items:string[])=>[...new Set(items)];
const id=createMockId;
export function newMatchingConversation(now=new Date()):MatchingConversation{return {id:id(),title:'새 대화',createdAt:now.toISOString(),updatedAt:now.toISOString(),requirements:structuredClone(emptyChatRequirements),messages:[]};}
export function inferChatRequirements(previous:ChatRequirements,text:string):ChatRequirements{
 const next=structuredClone(previous);const lower=text.toLowerCase();const a=analyzeMatching({description:text,stacks:[],interest:'전체 분야'});
 if(!next.description&&text.trim().length>=10)next.description=text.trim();
 next.stacks=unique([...next.stacks,...a.inferredStacks]);next.interests=unique([...next.interests,...a.inferredInterests]);
 const matchesAlias=(alias:string)=>/^[a-z]+$/i.test(alias)?new RegExp(`(^|[^a-z])${alias}($|[^a-z])`,'i').test(lower):lower.includes(alias);
 next.fields=unique([...next.fields,...Object.keys(chatDomainAliases).filter(k=>chatDomainAliases[k].some(matchesAlias))]);
 for(const [name,aliases] of Object.entries(chatRoleAliases))if(aliases.some(matchesAlias)){
  const existing=next.roles.find(r=>r.name===name);let count=existing?.count||1;
  for(const alias of aliases){const escaped=alias.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');const found=lower.match(new RegExp(escaped+'(?:\\s*개발자)?\\s*(\\d{1,2})\\s*명'));if(found)count=Number(found[1]);}
  if(existing)existing.count=count;else next.roles.push({name,count});
 }
 if(/경력\s*무관|신입\s*가능/.test(text))next.career='경력 무관';else {const career=text.match(/(?:경력\s*)?(\d+)\s*년\s*이상/);if(career)next.career=career[1]+'년 이상';}
 const duration=text.match(/(\d{1,2})\s*개월/);if(duration)next.duration=duration[1]+'개월';
 return next;
}
export function missingChatRequirements(r:ChatRequirements){return (['interests','career','duration'] as const).filter(key=>!r[key].length);}
export function addChatStack(stacks:string[],value:string){const name=value.trim();if(!name||name.length>50)throw new Error('기술 스택은 1~50자로 입력해 주세요.');const normalized=stackOptions.find(option=>option.toLowerCase()===name.toLowerCase())||name;if(stacks.includes(normalized))throw new Error('이미 추가한 기술 스택입니다.');if(stacks.length>=30)throw new Error('기술 스택은 최대 30개까지 추가할 수 있습니다.');return [...stacks,normalized];}
export function validateChatRequirements(r:ChatRequirements){if(r.description.length>1500||r.fields.length>12||r.interests.length>12||r.roles.length>12||r.fields.some(x=>!x.trim()||x.length>30)||r.roles.some(x=>!x.name.trim()||x.name.trim().length>30||!Number.isInteger(x.count)||x.count<1||x.count>20)||r.stacks.length>30||r.stacks.some(x=>!x.trim()||x.trim().length>50)||r.interests.some(x=>!chatInterestOptions.includes(x))||r.career&&!chatCareerOptions.includes(r.career)||r.duration&&!/^([1-9]|[1-2]\d|3[0-6])개월$/.test(r.duration))throw new Error('선택 조건과 입력 길이를 확인해 주세요. 기간은 1~36개월입니다.');}
export function roleMatches(request:string,actual:string){const aliases=chatRoleAliases[request]||[request];return aliases.some(alias=>actual.toLowerCase().includes(alias.toLowerCase()))||(request==='UI/UX'&&actual.includes('디자이너'));}
export function buildChatResult(r:ChatRequirements,candidates:(Developer&{available?:boolean;jobs?:string[]})[],projects:Project[]):MatchingResult{
 const base=buildMatchingResult({description:r.description,stacks:r.stacks,interest:r.interests[0]||'전체 분야',skipRole:true},candidates,projects);
 const fieldHits=(stacks:string[],text:string)=>r.fields.filter(f=>(chatDomainStacks[f]||[]).some(s=>stacks.includes(s))||text.toLowerCase().includes(f.toLowerCase()));
 const developers=candidates.map(d=>{const roles=r.roles.filter(v=>[d.role,...(d.jobs||[])].some(job=>roleMatches(v.name,job)));const tech=d.stacks.filter(s=>r.stacks.includes(s));const interests=d.interests.filter(v=>r.interests.includes(v));const fields=fieldHits(d.stacks,[d.bio,...d.interests].join(' '));const years=Number(d.career.match(/\d+/)?.[0]||0);const enough=years>=Number(r.career.match(/\d+/)?.[0]||0);const demand=roles.reduce((n,v)=>n+v.count,0);
 return {...d,available:d.available!==false,score:Math.max(20,Math.min(98,25+Math.min(24,tech.length*8)+Math.min(18,fields.length*9)+Math.min(12,interests.length*6)+(roles.length?20+Math.min(5,demand):0)+(enough?8:-12)+(d.available!==false?3:-8))),reasons:[`모집 직군: ${roles.length?roles.map(v=>v.name+' '+v.count+'명').join(', '):'직접 일치 없음 (탐색 후보)'}`,`프로젝트 분야: ${fields.length?fields.join(', '):'직접 일치 없음'}`,`일치 기술: ${tech.join(', ')||'없음'} · 관심 분야: ${interests.join(', ')||'없음'}`,`요구 경력 ${r.career}: ${enough?'충족':'미달'} · 예상 기간 ${r.duration}은 협의 필요`,d.available!==false?'협업 가능':'협업 쉬는 중, 연락 전 확인 필요']};}).sort((a,b)=>b.score-a.score||a.id.localeCompare(b.id));
 const similar=projects.map(p=>{const tech=p.stacks.filter(s=>r.stacks.includes(s));const fields=fieldHits(p.stacks,p.title+' '+p.summary+' '+p.field);const interests=r.interests.includes(p.field);const roles=r.roles.filter(v=>p.roles.some(role=>roleMatches(v.name,role)));return {...p,score:Math.min(98,25+Math.min(28,tech.length*9)+Math.min(20,fields.length*10)+(interests?15:0)+Math.min(10,roles.length*5)),reasons:[`분야 ${fields.join(', ')||'직접 일치 없음'} · 관심 ${interests?p.field:'직접 일치 없음'}`,`일치 기술 ${tech.join(', ')||'없음'} · 모집 직군 ${roles.map(v=>v.name).join(', ')||'직접 일치 없음'}`]};}).sort((a,b)=>b.score-a.score||b.createdAt.localeCompare(a.createdAt)).slice(0,3);
 return {...base,analysis:{...base.analysis,stacks:r.stacks,interests:r.interests,role:r.roles.map(v=>v.name).join(' / '),missing:[]},developers:developers.slice(0,6),projects:similar};
}
export function sendMatchingMessage(conversation:MatchingConversation,text:string,requirements:ChatRequirements,now=new Date()):MatchingConversation{
 if(!text.trim()||text.length>1500)throw new Error('메시지를 1~1,500자로 입력해 주세요.');
 validateChatRequirements(requirements);
 const next=structuredClone(conversation);const at=now.toISOString();
 if(!next.messages.length)next.title=text.trim().slice(0,32);
 next.requirements=structuredClone(requirements);next.updatedAt=at;next.draft='';
 next.messages.push({id:id(),role:'user',text:text.trim(),createdAt:at,requirements:structuredClone(requirements)});
 return next;
}
function validRequirements(v:unknown):v is ChatRequirements{if(!v||typeof v!=='object')return false;const r=v as ChatRequirements;if(typeof r.description!=='string'||typeof r.career!=='string'||typeof r.duration!=='string'||!['fields','interests','stacks'].every(k=>Array.isArray(r[k as 'fields'])&&r[k as 'fields'].every(x=>typeof x==='string'))||!Array.isArray(r.roles)||!r.roles.every(x=>x&&typeof x.name==='string'&&Number.isInteger(x.count)&&x.count>=1&&x.count<=20))return false;try{validateChatRequirements(r);return true;}catch{return false;}}
export function parseMatchingChat(value:unknown):MatchingChatState{
 const out=structuredClone(initialMatchingChat);if(!value||typeof value!=='object')return out;const raw=value as MatchingChatState;
 if(raw.conversations&&typeof raw.conversations==='object'&&!Array.isArray(raw.conversations))for(const [owner,items] of Object.entries(raw.conversations)){if(!Array.isArray(items))continue;out.conversations[owner]=items.map(c=>c&&c.requirements&&Array.isArray(c.requirements.fields)&&Array.isArray(c.requirements.interests)?{...c,requirements:{...c.requirements,fields:c.requirements.fields.filter((value: string)=>!chatInterestOptions.includes(value)),interests:unique([...c.requirements.interests,...c.requirements.fields.filter((value: string)=>chatInterestOptions.includes(value))])}}:c).filter(c=>c&&typeof c.id==='string'&&typeof c.title==='string'&&[c.createdAt,c.updatedAt].every(d=>typeof d==='string'&&Number.isFinite(Date.parse(d)))&&(c.draft===undefined||typeof c.draft==='string'&&c.draft.length<=1500)&&validRequirements(c.requirements)&&Array.isArray(c.messages)&&c.messages.every(m=>m&&typeof m.id==='string'&&['user','assistant'].includes(m.role)&&typeof m.text==='string'&&typeof m.createdAt==='string'&&Number.isFinite(Date.parse(m.createdAt))&&(!m.requirements||validRequirements(m.requirements))&&(!m.result||!!parseMatchingState({last:{check:{inputKey:'chat',createdAt:m.createdAt,result:m.result}}}).last.check)));}
 if(raw.active&&typeof raw.active==='object')for(const [owner,active] of Object.entries(raw.active))if(typeof active==='string'&&out.conversations[owner]?.some(c=>c.id===active))out.active[owner]=active;return out;
}
