export type KickVoteStatus='active'|'passed'|'rejected'|'expired'|'cancelled';
export interface ChatKickVote {id:string;projectId:string;targetId:string;createdBy:string;reason:string;eligibleVoterIds:string[];ballots:Record<string,'yes'|'no'>;createdAt:string;expiresAt:string;status:KickVoteStatus;resolvedAt?:string;resolutionReason?:string}
export type ChatKickAction={type:'start';projectId:string;targetId:string;reason:string}|{type:'vote';voteId:string;choice:'yes'|'no'};
