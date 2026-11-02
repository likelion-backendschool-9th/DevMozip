import {vi} from 'vitest';
import {createElement} from 'react';
import {fixture} from './helpers/RouterFixture';
vi.mock('next/navigation',()=>({
 usePathname:()=>new URL(fixture.url,'http://localhost').pathname,
 useSearchParams:()=>new URLSearchParams(new URL(fixture.url,'http://localhost').search),
 useParams:()=>fixture.params,
 useRouter:()=>({push:(url:string)=>{fixture.url=url;},replace:(url:string)=>{fixture.url=url;},back:()=>{},forward:()=>{}})
}));
vi.mock('next/link',()=>({default:({href,children,prefetch,replace,scroll,...props}:Record<string,unknown>)=>createElement('a',{...props,href},children as never)}));
