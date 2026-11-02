'use client';
import {useCallback,useSyncExternalStore} from 'react';
import {usePathname,useRouter,useSearchParams} from 'next/navigation';
// URL helpers only. Route ownership stays in app/**/page.tsx.
export function useNavigation(){const router=useRouter();return useCallback((url:string|number,options?:{replace?:boolean})=>{if(typeof url==='number'){if(url<0)router.back();else router.forward();return;}if(options?.replace)router.replace(url);else router.push(url);},[router]);}
export function useQueryState(){const pathname=usePathname();const search=useSearchParams();const router=useRouter();const params=new URLSearchParams(search.toString());const setParams=(next:URLSearchParams|Record<string,string>|string,options?:{replace?:boolean})=>{const query=new URLSearchParams(next).toString();const url=pathname+(query?'?'+query:'');if(options?.replace)router.replace(url,{scroll:false});else router.push(url,{scroll:false});};return [params,setParams] as const;}
const subscribeHash=(notify:()=>void)=>{window.addEventListener('hashchange',notify);window.addEventListener('popstate',notify);return()=>{window.removeEventListener('hashchange',notify);window.removeEventListener('popstate',notify);};};
export function useCurrentLocation(){const pathname=usePathname();const params=useSearchParams();const hash=useSyncExternalStore(subscribeHash,()=>window.location.hash,()=> '');return {pathname,search:params.size?'?'+params.toString():'',hash,key:pathname+'?'+params.toString()+hash};}
