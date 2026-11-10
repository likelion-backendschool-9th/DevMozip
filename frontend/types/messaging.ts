import type {NoticeAction} from '@/types/projectReview';
// GET·POST /api/messages/chats 서버 응답. tag와 nickname은 아직 설정하지 않은 회원이면 null이다.
export interface DirectChatPeer {tag:string|null;nickname:string|null;profileImage:string|null;jobCategories:string[];collaborationStatus:string|null}
export interface DirectChatMessage {id:number;body:string;mine:boolean;createdAt:string}
export interface DirectChatSummary {id:number;peer:DirectChatPeer;lastMessage:DirectChatMessage;unreadCount:number}
export interface DirectChatList {items:DirectChatSummary[];nextCursor:number|null}
export interface DirectChatDetail {id:number;peer:DirectChatPeer;canSend:boolean;peerLastReadMessageId:number|null;messages:DirectChatMessage[];nextBefore:number|null}
// GET·POST /api/messages/projects 서버 응답. id·projectId는 프로젝트 ID, members[].id와 sender.memberId는 그 방의 참여 행 ID다.
export type ProjectChatStatus='RECRUITING'|'RECRUITMENT_COMPLETED'|'IN_PROGRESS'|'COMPLETED';
export interface ProjectChatSummary {id:number;title:string;status:ProjectChatStatus;memberCount:number;unreadCount:number}
export interface ProjectChatList {items:ProjectChatSummary[];nextCursor:number|null}
export interface ProjectChatMember {id:number;me:boolean;tag:string|null;nickname:string|null;profileImage:string|null;role:'HOST'|'PARTICIPANT'|null;lastReadMessageId:number|null;joinedAt:string;leftAt:string|null}
export interface ProjectChatMessage {id:number;body:string;mine:boolean;sender:{memberId:number;tag:string|null;nickname:string|null};createdAt:string}
export interface ProjectChatDetail {projectId:number;title:string;status:ProjectChatStatus;canSend:boolean;isHost:boolean;members:ProjectChatMember[];messages:ProjectChatMessage[];nextBefore:number|null}
export interface ChatMessage {id:string;senderId:string;body:string;kind:'text'|'proposal'|'system';proposalTitle?:string;createdAt:string;readBy:string[]}
export interface DirectConversation {id:string;participants:string[];messages:ChatMessage[]}
export interface ProjectRoom {projectId:string;createdAt:string;memberIds:string[];messages:ChatMessage[]}
export interface UserReport {id:string;reporterId:string;targetId:string;reason:string;createdAt:string}
export interface MessagingState {version:1;conversations:DirectConversation[];rooms:Record<string,ProjectRoom>;notificationReads:Record<string,string[]>;userReports:UserReport[]}
export interface ActivityNotice {id:string;recipientId:string;actorId:string;kind:'follow'|'like'|'comment'|'repost';title:string;body:string;href:string;createdAt:string;eventKey:string}
export interface UnifiedNotice {actions?:NoticeAction[];resolved?:boolean;id:string;recipientId:string;kind:'project'|'following'|'follow'|'like'|'comment'|'repost';title:string;body:string;href:string;createdAt:string}
export type MessagingAction={type:'send-direct';targetId:string;body:string;kind:'text'|'proposal';proposalTitle?:string}|{type:'send-group';projectId:string;body:string}|{type:'read-direct';conversationId:string}|{type:'read-group';projectId:string}|{type:'report-user';targetId:string;reason:string}|{type:'notice-read';id:string;read:boolean}|{type:'notices-read-all'};
