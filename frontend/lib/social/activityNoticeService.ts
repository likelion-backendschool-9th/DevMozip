import { createMockId } from '@/lib/utils/createMockId';
import type { ProfileState } from '@/types/profile';
import type { ActivityNotice } from '@/types/messaging';
export function addActivityNotice(state:ProfileState,actorId:string,recipientId:string,kind:ActivityNotice['kind'],recordId:string,title:string,body:string,href:string,now:Date){if(actorId===recipientId)return;const eventKey=`${kind}:${actorId}:${recordId}`;if(state.socialNotices.some(n=>n.recipientId===recipientId&&n.eventKey===eventKey))return;state.socialNotices.unshift({id:createMockId(),actorId,recipientId,kind,title,body,href,createdAt:now.toISOString(),eventKey});}
