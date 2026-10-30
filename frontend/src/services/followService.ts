import { defaultFollowPreferences } from '../mocks/follow';
import { createMockId } from '../utils/createMockId';
import type { ProfileState } from '../types/profile';
export function followPreferences(state:ProfileState,actorId:string,targetId:string){return state.followPreferences[actorId]?.[targetId]||{...defaultFollowPreferences};}
export function followersOf(state:ProfileState,targetId:string){return Object.keys(state.following).filter(id=>state.following[id].includes(targetId));}
export function publishFollowEvent(state:ProfileState,actorId:string,kind:'activity'|'project',recordId:string,title:string,now:Date){for(const id of followersOf(state,actorId)){const preferences=followPreferences(state,id,actorId);if(!(kind==='activity'?preferences.activity:preferences.projects))continue;if(state.followNotices.some(n=>n.recipientId===id&&n.actorId===actorId&&n.kind===kind&&n.recordId===recordId))continue;state.followNotices.unshift({id:createMockId(),recipientId:id,actorId,kind,recordId,title,createdAt:now.toISOString()});}}
