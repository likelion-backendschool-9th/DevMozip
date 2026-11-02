'use client';
import { createContext,useContext,useRef,useState,useEffect,type ReactNode } from 'react';
import { freshHomeState,loadHomeState,saveHomeState } from '@/lib/matching/homeService';
import { consumeMatching,inputKey,matchingUsage } from '@/lib/matching/matchingService';
import { newMatchingConversation,sendMatchingMessage } from '@/lib/matching/matchingChatService';
import { temperature } from '@/lib/profiles/profileService';
import { usePrototype } from '@/lib/state/PrototypeProvider';
import { useProfiles } from '@/lib/state/ProfileProvider';
import { useAuth } from '@/lib/state/AuthProvider';
import { useProjects } from '@/lib/state/ProjectProvider';
import type { DemoRole,HomeState } from '@/types/home';
import type { MatchingSnapshot } from '@/types/matching';
import type { ChatRequirements,MatchingConversation } from '@/types/matchingChat';
interface HomeContextValue {
 state:HomeState&{role:DemoRole;following:string[]};usage:ReturnType<typeof matchingUsage>;result:MatchingSnapshot|null;
 run:()=>{ok:boolean;error?:string};update:(patch:Partial<HomeState>)=>void;reset:()=>void;resetAll:()=>void;
 conversations:MatchingConversation[];conversation:MatchingConversation|null;
 newChat:(description?:string)=>void;selectChat:(id:string)=>void;deleteChat:(id:string)=>void;
 editChat:(patch:Partial<ChatRequirements>)=>void;sendChat:(text:string)=>{ok:boolean;error?:string};
}
const Context=createContext<HomeContextValue|null>(null);
export function HomeProvider({children}:{children:ReactNode}){
 const {user,publicUsers,canViewProfile}=useAuth();const {state:profileState}=useProfiles();const {projects}=useProjects();const {notify}=usePrototype();
 const [state,setState]=useState(loadHomeState);const current=useRef(state);current.current=state;
 // 월 경계와 탭 복귀 시 현재 한국 시각의 한도를 다시 표시합니다.
 const [clock,setClock]=useState(()=>new Date());useEffect(()=>{const refresh=()=>setClock(new Date());const timer=setInterval(refresh,30000);window.addEventListener('focus',refresh);return()=>{clearInterval(timer);window.removeEventListener('focus',refresh);};},[]);
 const commit=(next:HomeState)=>{current.current=next;setState(next);if(!saveHomeState(next))notify('저장 공간을 사용할 수 없어 대화와 추천 사용량이 이번 세션에만 유지됩니다.');};
 const candidates=()=>{const excluded=publicUsers.filter(u=>u.role==='admin').map(u=>u.id);return Object.values(profileState.profiles).filter(p=>!excluded.includes(p.id)&&canViewProfile(p.id)).map(p=>({id:p.id,name:p.nickname,role:p.job,jobs:p.jobs,career:p.career,bio:p.bio,stacks:p.stacks,interests:p.interests,color:p.color,temperature:temperature(profileState.reviews,p.id),available:p.available}));};
 const run=()=>{try{const next=consumeMatching(current.current.matching,user,current.current.input,candidates(),projects);commit({...current.current,matching:next.state});return {ok:true};}catch(error){const message=error instanceof Error?error.message:'추천하지 못했습니다.';notify(message);return {ok:false,error:message};}};
 const owner=user?.id||'guest';const conversations=state.chat.conversations[owner]||[];const active=state.chat.active[owner];const conversation=conversations.find(c=>c.id===active)||null;
 const saveConversation=(c:MatchingConversation,matching=current.current.matching)=>{const chat=structuredClone(current.current.chat);const items=chat.conversations[owner]||[];chat.conversations[owner]=[c,...items.filter(v=>v.id!==c.id)];chat.active[owner]=c.id;commit({...current.current,chat,matching});};
 const fail=(error:unknown)=>{const message=error instanceof Error?error.message:'대화를 저장하지 못했습니다.';notify(message);return {ok:false,error:message};};
 const newChat=(description?:string)=>{try{const c=newMatchingConversation();if(description){const next=sendMatchingMessage(c,description,c.requirements,current.current.matching,user,candidates(),projects);saveConversation(next.conversation,next.matching);}else saveConversation(c);}catch(e){fail(e);}};
 const snapshot=user?state.matching.last[user.id]:null;
 return <Context.Provider value={{state:{...state,role:user?.role||'guest',following:user?(profileState.following[user.id]||[]):[]},usage:matchingUsage(state.matching,user,clock),result:snapshot&&snapshot.inputKey===inputKey(state.input)?{...snapshot,result:{...snapshot.result,developers:snapshot.result.developers.filter(d=>canViewProfile(d.id))}}:null,run,update:patch=>commit({...current.current,...patch}),reset:()=>commit({...current.current,input:structuredClone(freshHomeState().input)}),resetAll:()=>commit(freshHomeState()),conversations,conversation,newChat,
 selectChat:id=>{if((current.current.chat.conversations[owner]||[]).some(c=>c.id===id))commit({...current.current,chat:{...current.current.chat,active:{...current.current.chat.active,[owner]:id}}});},
 deleteChat:id=>{const chat=structuredClone(current.current.chat);chat.conversations[owner]=(chat.conversations[owner]||[]).filter(c=>c.id!==id);if(chat.active[owner]===id)chat.active[owner]=chat.conversations[owner][0]?.id||'';commit({...current.current,chat});},
 editChat:patch=>{const c=(current.current.chat.conversations[owner]||[]).find(v=>v.id===current.current.chat.active[owner])||newMatchingConversation();saveConversation({...c,requirements:{...c.requirements,...patch}});},
 sendChat:text=>{try{const c=(current.current.chat.conversations[owner]||[]).find(v=>v.id===current.current.chat.active[owner])||newMatchingConversation();const next=sendMatchingMessage(c,text,c.requirements,current.current.matching,user,candidates(),projects);saveConversation(next.conversation,next.matching);return {ok:true};}catch(e){return fail(e);}}
 }}>{children}</Context.Provider>;
}
export function useHome(){const value=useContext(Context);if(!value)throw new Error('HomeProvider required');return value;}
