'use client';
import {moderateProfile,type ModerationAction} from '@/lib/profiles/moderationService';
import { createContext, useContext, useRef, useState, type ReactNode } from 'react';
import { allProfiles, applyProfileAction, freshProfileState, loadProfiles, saveProfiles } from '@/lib/profiles/profileService';
import { useAuth } from '@/lib/state/AuthProvider';
import { usePrototype } from '@/lib/state/PrototypeProvider';
import type { ProfileAction, ProfileState, Collaboration } from '@/types/profile';
interface ProfileContextValue{moderate:(action:ModerationAction)=>boolean;state:ProfileState;act:(action:ProfileAction,history?:Collaboration[],now?:Date)=>boolean;reset:()=>void}
const Context=createContext<ProfileContextValue|null>(null);
export function ProfileProvider({children}:{children:ReactNode}){
 const {user,publicUsers,administration,syncProfileName,resetProfileNames}=useAuth();const {notify}=usePrototype();const [stored,setStored]=useState(loadProfiles);const current=useRef(stored);const state={...stored,profiles:allProfiles(stored,publicUsers)};current.current=state;
 const commit=(next:ProfileState)=>{current.current=next;setStored(next);return saveProfiles(next);};
 const act=(action:ProfileAction,history?:Collaboration[],now=new Date())=>{try{if(action.type==='review')throw new Error('프로젝트 리뷰 요청 링크에서 작성해 주세요.');const next=applyProfileAction(current.current,user?.id||null,action,now,history,administration.keywords);if(action.type==='social-view'&&JSON.stringify(next)===JSON.stringify(current.current))return true;const saved=commit(next);if((action.type==='profile'||action.type==='onboarding')&&user)syncProfileName(user.id,next.profiles[user.id].nickname);if(action.type!=='social-view'&&action.type!=='follow-publish')notify(saved?'변경사항을 반영했습니다.':'변경했지만 저장 공간을 사용할 수 없어 이번 세션에만 유지됩니다.');return true;}catch(error){notify(error instanceof Error?error.message:'변경하지 못했습니다.');return false;}};
 return <Context.Provider value={{state,act,moderate:action=>{try{const saved=commit(moderateProfile(current.current,user,action));notify(saved?'콘텐츠 관리를 반영했습니다.':'현재 세션에만 유지됩니다.');return true;}catch(e){notify(e instanceof Error?e.message:'변경 실패');return false;}},reset:()=>{resetProfileNames();commit(freshProfileState());}}}>{children}</Context.Provider>;
}
export function useProfiles(){const v=useContext(Context);if(!v)throw new Error('ProfileProvider required');return v;}
