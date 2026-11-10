'use client';
import {useEffect} from 'react';
import {createLocalStore,useStore} from '@/lib/localStore';
import {useAuth} from '@/lib/state/authStore';
import {projectRequest,ProjectApiError} from './useProjectApi';
import {notify} from '@/lib/state/prototypeStore';
import type {ProjectListRecord} from '@/types/projectApi';
type State={owner:string;items:ProjectListRecord[];loading:boolean;ready:boolean;pending:boolean;error:ProjectApiError|null};
const empty:State={owner:'',items:[],loading:false,ready:false,pending:false,error:null};
export const projectLikes=createLocalStore<State>(()=>empty,true);
let requestVersion=0;
export async function loadProjectLikes(owner:string,force=false){
 const state=projectLikes.get();if(state.owner===owner&&(state.loading||state.ready&&!force))return false;
 const version=++requestVersion;
 projectLikes.set({...empty,...(state.owner===owner?state:{}),owner,loading:!!owner,error:null});if(!owner)return true;
 try{const items=await projectRequest<ProjectListRecord[]>('/likes');if(version!==requestVersion||projectLikes.get().owner!==owner)return false;projectLikes.set({...projectLikes.get(),items,loading:false,ready:true});return true;}
 catch(e){if(version===requestVersion&&projectLikes.get().owner===owner)projectLikes.set({...projectLikes.get(),items:[],loading:false,ready:true,error:e instanceof ProjectApiError?e:new ProjectApiError('좋아요를 불러오지 못했습니다.',502)});return false;}
}
export async function toggleProjectLike(owner:string,id:number){
 const state=projectLikes.get();if(!owner||state.owner!==owner||state.pending||state.loading||state.error)return;
 const saved=state.items.some(p=>p.id===id);projectLikes.set({...state,pending:true});
 try{await projectRequest('/'+id+'/like',saved?'DELETE':'POST');if(projectLikes.get().owner!==owner)return;const updated=await loadProjectLikes(owner,true);notify(updated?(saved?'좋아요를 취소했습니다.':'좋아요했습니다.'):'저장 결과를 다시 불러오지 못했습니다. 새로고침해 주세요.');}
 catch(e){if(projectLikes.get().owner===owner)notify(e instanceof Error?e.message:'좋아요를 변경하지 못했습니다.');}
 finally{if(projectLikes.get().owner===owner)projectLikes.set({...projectLikes.get(),pending:false});}
}
export function useProjectLikes(enabled=true){
 const {user}=useAuth();const owner=enabled?user?.email??'':'';const state=useStore(projectLikes);
 useEffect(()=>{if(!enabled)return;void loadProjectLikes(owner);const refresh=()=>{void loadProjectLikes(owner,true);};window.addEventListener('focus',refresh);return()=>window.removeEventListener('focus',refresh);},[owner,enabled]);
 return {...(state.owner===owner?state:empty),owner,reload:()=>loadProjectLikes(owner,true),toggle:(id:number)=>toggleProjectLike(owner,id)};
}
