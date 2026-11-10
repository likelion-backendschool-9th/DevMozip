'use client';
import {useEffect} from 'react';
import {createLocalStore,useStore} from '@/lib/localStore';
import {useAuth} from '@/lib/state/authStore';
import {projectRequest,ProjectApiError} from './useProjectApi';
import {notify} from '@/lib/state/prototypeStore';
import type {ProjectListRecord} from '@/types/projectApi';
type State={owner:string;items:ProjectListRecord[];loading:boolean;ready:boolean;pending:boolean;error:ProjectApiError|null};
const empty:State={owner:'',items:[],loading:false,ready:false,pending:false,error:null};
export const projectBookmarks=createLocalStore<State>(()=>empty,true);
let requestVersion=0;
export async function loadProjectBookmarks(owner:string,force=false){
 const state=projectBookmarks.get();if(state.owner===owner&&(state.loading||state.ready&&!force))return false;
 const version=++requestVersion;
 projectBookmarks.set({...empty,...(state.owner===owner?state:{}),owner,loading:!!owner,error:null});if(!owner)return true;
 try{const items=await projectRequest<ProjectListRecord[]>('/bookmarks');if(version!==requestVersion||projectBookmarks.get().owner!==owner)return false;projectBookmarks.set({...projectBookmarks.get(),items,loading:false,ready:true});return true;}
 catch(e){if(version===requestVersion&&projectBookmarks.get().owner===owner)projectBookmarks.set({...projectBookmarks.get(),items:[],loading:false,ready:true,error:e instanceof ProjectApiError?e:new ProjectApiError('북마크를 불러오지 못했습니다.',502)});return false;}
}
export async function toggleProjectBookmark(owner:string,id:number){
 const state=projectBookmarks.get();if(!owner||state.owner!==owner||state.pending||state.loading||state.error)return;
 const saved=state.items.some(p=>p.id===id);projectBookmarks.set({...state,pending:true});
 try{await projectRequest('/'+id+'/bookmark',saved?'DELETE':'POST');if(projectBookmarks.get().owner!==owner)return;const updated=await loadProjectBookmarks(owner,true);notify(updated?(saved?'북마크를 해제했습니다.':'북마크에 저장했습니다.'):'저장 결과를 다시 불러오지 못했습니다. 새로고침해 주세요.');}
 catch(e){if(projectBookmarks.get().owner===owner)notify(e instanceof Error?e.message:'북마크를 변경하지 못했습니다.');}
 finally{if(projectBookmarks.get().owner===owner)projectBookmarks.set({...projectBookmarks.get(),pending:false});}
}
export function useProjectBookmarks(enabled=true){
 const {user}=useAuth();const owner=enabled?user?.email??'':'';const state=useStore(projectBookmarks);
 useEffect(()=>{if(!enabled)return;void loadProjectBookmarks(owner);const refresh=()=>{void loadProjectBookmarks(owner,true);};window.addEventListener('focus',refresh);return()=>window.removeEventListener('focus',refresh);},[owner,enabled]);
 return {...(state.owner===owner?state:empty),owner,reload:()=>loadProjectBookmarks(owner,true),toggle:(id:number)=>toggleProjectBookmark(owner,id)};
}
