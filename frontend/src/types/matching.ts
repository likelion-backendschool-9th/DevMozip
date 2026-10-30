import type { Developer, Project, RecommendationInput } from './home';
export type QuestionKey='stacks'|'interest'|'role';
export interface MatchingAnalysis { stacks:string[];interests:string[];role:string;inferredStacks:string[];inferredInterests:string[];inferredRole:string;missing:QuestionKey[] }
export interface DeveloperMatch extends Developer {available:boolean;score:number;reasons:string[]}
export interface ProjectMatch extends Project {score:number;reasons:string[]}
export interface MatchingResult {analysis:MatchingAnalysis;developers:DeveloperMatch[];projects:ProjectMatch[]}
export interface MatchingSnapshot {inputKey:string;createdAt:string;result:MatchingResult}
export interface MatchingState {usage:Record<string,Record<string,number>>;last:Record<string,MatchingSnapshot>}
