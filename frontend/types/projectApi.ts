export type ProjectRoleCode = string; // 기본 코드와 사용자 직접 입력 직군을 함께 사용한다.
export interface CreateProjectRequest {
    title: string; summary: string | null; description: string; goal: string | null;
    imageUrl: string; isPublic: boolean;
    recruitmentSlots: { roleCode: ProjectRoleCode; capacity: number; minimumYears: number }[];
    techStacks: string[]; interests: string[];
    recruitmentStart: string; recruitmentEnd: string; developmentStart: string; developmentEnd: string;
}
export interface ProjectCreationReceipt { id: number; title: string }

export type ProjectStatusCode = 'RECRUITING' | 'RECRUITMENT_COMPLETED' | 'IN_PROGRESS' | 'COMPLETED';
export interface ProjectRecord extends CreateProjectRequest {
 id:number; hostMemberId:number; status:ProjectStatusCode; totalCapacity:number; viewCount:number; createdAt:string;
}
export interface ProjectListRecord extends ProjectRecord { memberCount:number; myRole:'HOST'|'PARTICIPANT'|null; recruitmentClosed:boolean }
export interface ProjectPageResult {items:ProjectListRecord[];totalElements:number;page:number;size:number;totalPages:number}
export interface ProjectMembership {myRole:'HOST'|'PARTICIPANT';myEndRequested:boolean;endRequestCount:number;requiredEndRequestCount:number}
export interface ProjectTeamMember {userId:number;tag:string|null;nickname:string|null;role:'HOST'|'PARTICIPANT';jobRole:string|null}
export interface ProjectDetailResult {team?:ProjectTeamMember[];project:ProjectRecord;memberCount:number;recruitmentClosed:boolean;membership:ProjectMembership|null;host:{id:number;tag:string|null;nickname:string|null}|null}
export interface ProjectIdentity {id:number;role:string}
export interface ProjectLifecycleResult {project:ProjectRecord;activeMemberCount:number;endRequestCount:number;requiredEndRequestCount:number;myEndRequested:boolean}
export interface ProjectOptions {jobRoles:{code:ProjectRoleCode;label:string}[];techStacks:string[];interests:string[]}
