import type {NoticeAction} from '@/types/projectReview';
export interface ChatMessage {id:string;senderId:string;body:string;kind:'text'|'proposal'|'system';proposalTitle?:string;createdAt:string;readBy:string[]}
export interface DirectConversation {id:string;participants:string[];messages:ChatMessage[]}
export interface ProjectRoom {projectId:string;createdAt:string;memberIds:string[];messages:ChatMessage[]}
export interface UserReport {id:string;reporterId:string;targetId:string;reason:string;createdAt:string}
export interface MessagingState {version:1;conversations:DirectConversation[];rooms:Record<string,ProjectRoom>;notificationReads:Record<string,string[]>;userReports:UserReport[]}
export interface ActivityNotice {id:string;recipientId:string;actorId:string;kind:'follow'|'like'|'comment'|'repost';title:string;body:string;href:string;createdAt:string;eventKey:string}
export interface UnifiedNotice {actions?:NoticeAction[];resolved?:boolean;id:string;recipientId:string;kind:'project'|'following'|'follow'|'like'|'comment'|'repost';title:string;body:string;href:string;createdAt:string}
export type MessagingAction={type:'send-direct';targetId:string;body:string;kind:'text'|'proposal';proposalTitle?:string}|{type:'send-group';projectId:string;body:string}|{type:'read-direct';conversationId:string}|{type:'read-group';projectId:string}|{type:'report-user';targetId:string;reason:string}|{type:'notice-read';id:string;read:boolean}|{type:'notices-read-all'};
