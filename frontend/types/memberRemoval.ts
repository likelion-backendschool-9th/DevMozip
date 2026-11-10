export interface MemberRemovalVote {
 id:number; projectId:number; targetMemberId:number; targetNickname:string; reason:string;
 status:'ACTIVE'|'PASSED'|'REJECTED'|'EXPIRED'|'CANCELLED'; yes:number; no:number; eligibleVoterCount:number; requiredCount:number;
 myVote:boolean|null; canVote:boolean; createdAt:string; expiresAt:string; resolvedAt:string|null;
}
