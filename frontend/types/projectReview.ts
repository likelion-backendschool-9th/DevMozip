export interface ProjectReviewRequest {id:string;projectId:string;recipientId:string;targetIds:string[];token:string;issuedAt:string;expiresAt:string;usedAt?:string}
export interface TeamReviewInput {targetId:string;rating:number;keywords:string[]}
export interface NoticeAction {label:string;href:string}
