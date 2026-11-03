// Test-only URL fixture for existing static component assertions. Production
// route resolution is exclusively Next.js app/**/page.tsx.
import {Children,cloneElement,isValidElement,type ReactNode,type ReactElement} from 'react';
export const fixture={url:'/',params:{} as Record<string,string>};
export function MemoryRouter({initialEntries,children}:{initialEntries:string[];children:ReactNode}){fixture.url=initialEntries[0];fixture.params={};return children;}
type Definition={path?:string;element?:ReactElement<{children?:ReactNode}>;children?:ReactNode;index?:boolean};
export function Route(_props:Definition){return null;}
export function matchFixture(pathname:string,pattern:string){const keys:string[]=[];const source=pattern.split('/').map(part=>part.startsWith(':')?(keys.push(part.slice(1)),'([^/]+)'):part.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')).join('/');const match=pathname.match(new RegExp('^'+source+'$'));return match?Object.fromEntries(keys.map((key,i)=>[key,decodeURIComponent(match[i+1])])):null;}
export function Routes({children}:{children:ReactNode}){const pathname=new URL(fixture.url,'http://localhost').pathname;const find=(nodes:ReactNode):ReactNode=>{for(const node of Children.toArray(nodes)){if(!isValidElement<Definition>(node))continue;const {path,element,children:sub}=node.props;if(path){const params=matchFixture(pathname,path);if(params){fixture.params=params;return element;}}else if(sub){const result=find(sub);if(result)return element?cloneElement(element,{children:result}):result;}}return null;};return find(children);}
