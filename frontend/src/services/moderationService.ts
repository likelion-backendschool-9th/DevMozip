import type { AuthUser } from '../types/auth';
import type { ProfileState } from '../types/profile';
export type ModerationAction={type:'post-visibility';id:string;hidden:boolean}|{type:'comment-delete';id:string};
export function moderateProfile(state:ProfileState,actor:AuthUser|null,action:ModerationAction){if(actor?.role!=='admin')throw new Error('관리자만 콘텐츠를 관리할 수 있습니다.');const next=structuredClone(state);if(action.type==='post-visibility'){const p=next.posts.find(p=>p.id===action.id);if(!p)throw new Error('게시글을 찾을 수 없습니다.');p.hidden=action.hidden;}else{if(!next.social.comments.some(c=>c.id===action.id))throw new Error('댓글을 찾을 수 없습니다.');next.social.comments=next.social.comments.filter(c=>c.id!==action.id);}return next;}
