import type { AuthUser } from '@/types/auth';
import type { Profile } from '@/types/profile';
export function advancedSearch(items:Profile[],user:AuthUser|null,temperatures:Record<string,number>,minimum:number,sort:boolean){if(!user||user.role==='free')return items;const next=items.filter(p=>(temperatures[p.id]??36.5)>=minimum);return sort?[...next].sort((a,b)=>(temperatures[b.id]??36.5)-(temperatures[a.id]??36.5)||a.nickname.localeCompare(b.nickname,'ko')):next;}
