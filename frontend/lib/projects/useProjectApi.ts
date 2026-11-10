'use client';
import {useEffect,useState} from 'react';
import {useAuth} from '@/lib/state/authStore';
export class ProjectApiError extends Error{constructor(message:string,public status:number){super(message);}}
export async function projectRequest<T>(path:string,method='GET',body?:unknown,signal?:AbortSignal):Promise<T>{
 const response=await fetch('/api/projects'+path,{method,credentials:'same-origin',headers:body===undefined?undefined:{'Content-Type':'application/json'},body:body===undefined?undefined:JSON.stringify(body),signal,cache:'no-store'});
 if(response.status===204)return undefined as T;
 let data;try{data=await response.json();}catch{throw new ProjectApiError('서버 응답을 확인하지 못했습니다. 중복 요청 전에 저장 상태를 확인해 주세요.',502);}
 if(!response.ok)throw new ProjectApiError(typeof data.message==='string'?data.message:'요청을 처리하지 못했습니다.',response.status);
 return data as T;
}
export function useProjectResource<T>(path:string|null){
 const {user}=useAuth();const identity=user?.email??'';
 const [state,setState]=useState<{data:T|null;error:ProjectApiError|null;loading:boolean}>({data:null,error:null,loading:!!path});
 const [version,setVersion]=useState(0);
 useEffect(()=>{const abort=new AbortController();if(path===null){setState({data:null,error:null,loading:false});return;}
  setState({data:null,error:null,loading:true});projectRequest<T>(path,'GET',undefined,abort.signal).then(data=>{if(!abort.signal.aborted)setState({data,error:null,loading:false});}).catch(error=>{if(!abort.signal.aborted)setState({data:null,error:error instanceof ProjectApiError?error:new ProjectApiError('서버에 연결하지 못했습니다.',502),loading:false});});return()=>abort.abort();
 },[path,version,identity]);
 return {...state,reload:()=>setVersion(x=>x+1)};
}
