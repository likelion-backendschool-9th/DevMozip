import type {TeamReviewInput} from './projectReview';
import type { OnboardingDraft } from './account';
import type { ActivityNotice } from './messaging';
import type { FollowNotice, FollowPreferences } from './follow';
import type { PostImage, SocialAction, SocialState } from './social';
import type { Post } from './home';
export interface Profile { avatar?:string;jobs?:string[];customFields?:string[];projectInterests?:string[]; id:string; nickname:string; handle:string; job:string; career:string; bio:string; introduction:string; joinedAt:string; github:string; blog:string; contact:string; available:boolean; stacks:string[]; interests:string[]; color:string }
export interface Portfolio { id:string; ownerId:string; title:string; summary:string; start:string; end:string; role:string; stacks:string[]; description:string; features:string; challenge:string; outcome:string }
export type PortfolioInput = Omit<Portfolio,'id'|'ownerId'>;
export interface ProfilePost extends Post { hidden:boolean;images?:PostImage[];github?:string;external?:string;createdAt?:string;updatedAt?:string;views?:number }
export interface Repost { id:string; ownerId:string; originalId:string; opinion:string; hidden:boolean }
export type ActivityStatus='모집 중'|'모집 완료'|'프로젝트 진행 중'|'프로젝트 종료';
export interface Collaboration { projectId:string; start:string; end:string; status:ActivityStatus; endedAt?:string; members:{userId:string;role:string}[] }
export interface CollaborationReview { id:string; projectId:string; authorId:string; targetId:string; rating:number; keywords:string[]; createdAt:string }
export interface ProfileState { version:1;socialNotices:ActivityNotice[]; followPreferences:Record<string,Record<string,FollowPreferences>>;followNotices:FollowNotice[]; social:SocialState; profiles:Record<string,Profile>; portfolios:Portfolio[]; posts:ProfilePost[]; reposts:Repost[]; reviews:CollaborationReview[]; following:Record<string,string[]> }
export type ProfileEdit = Pick<Profile,'bio'|'introduction'|'github'|'blog'|'contact'|'available'|'stacks'|'interests'|'job'|'career'>;
export type ProfileAction = {type:'reviews-batch';projectId:string;entries:TeamReviewInput[]}| {type:'onboarding';input:OnboardingDraft}| {type:'follow-preferences';targetId:string;preferences:FollowPreferences}|{type:'follow-publish';kind:'activity'|'project';recordId:string;title:string}|SocialAction| {type:'profile';patch:ProfileEdit} | {type:'portfolio-save';id?:string;input:PortfolioInput} | {type:'portfolio-delete';id:string} | {type:'post-edit';id:string;title:string;body:string} | {type:'post-hide'|'post-delete';id:string} | {type:'repost-add';originalId:string;opinion?:string} | {type:'repost-edit';id:string;opinion:string} | {type:'repost-hide'|'repost-delete';id:string} | {type:'follow';targetId:string} | {type:'review';projectId:string;targetId:string;rating:number;keywords:string[]};
