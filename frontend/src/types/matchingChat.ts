import type { MatchingResult } from './matching';
export interface RecruitmentRole {name:string;count:number}
export interface ChatRequirements {description:string;fields:string[];interests:string[];stacks:string[];roles:RecruitmentRole[];career:string;duration:string}
export interface MatchingMessage {id:string;role:'user'|'assistant';text:string;createdAt:string;result?:MatchingResult;requirements?:ChatRequirements}
export interface MatchingConversation {id:string;title:string;createdAt:string;updatedAt:string;requirements:ChatRequirements;messages:MatchingMessage[]}
export interface MatchingChatState {conversations:Record<string,MatchingConversation[]>;active:Record<string,string>}
