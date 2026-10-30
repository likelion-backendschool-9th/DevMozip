import {createMockId} from '../utils/createMockId';
import type {ProjectNotice,RecruitmentState} from '../types/project';
export function addNotice(state:RecruitmentState,recipientId:string,projectId:string,title:string,body:string,now:Date,eventKey?:string,extra:Partial<ProjectNotice>={}){if(eventKey&&state.notifications.some(n=>n.eventKey===eventKey))return;state.notifications.unshift({...extra,id:createMockId(),recipientId,projectId,title,body,createdAt:now.toISOString(),eventKey});}
