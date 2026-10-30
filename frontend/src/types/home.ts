import type { MatchingChatState } from './matchingChat';
import type { MatchingState } from './matching';
export type DemoRole = 'guest' | 'free' | 'premium' | 'admin';
export interface Developer { id: string; name: string; role: string; career: string; bio: string; stacks: string[]; interests: string[]; color: string; temperature: number }
export interface Project { id: string; title: string; summary: string; field: string; roles: string[]; stacks: string[]; members: number; capacity: number; duration: string; deadline: string; createdAt: string; views: number; color: string; mark: string; status?: '모집 중' | '모집 완료' | '프로젝트 진행 중' | '프로젝트 종료' }
export type PostCategory = '정보' | '피드백' | '홍보' | '자유';
export interface Post { id: string; authorId: string; category: PostCategory; title: string; body: string; tags: string[]; likes: number; comments: number; color: string }
export interface RecommendationInput { description: string; stacks: string[]; interest: string;role?:string;skipStacks?:boolean;skipInterest?:boolean;skipRole?:boolean }
export interface HomeState { version: 1; chat:MatchingChatState; matching:MatchingState; input: RecommendationInput }
export type ProjectSort = 'latest' | 'popular' | 'deadline';
