export interface ManagedProjectReport {id:number;projectId:number;projectTitle:string;type:'PROJECT'|'MEMBER';reporterNickname:string;targetNickname:string|null;reason:string;status:string;handledBy:number|null;resolution:string|null;handledAt:string|null;createdAt:string}
export interface ProjectReportPage {items:ManagedProjectReport[];totalElements:number;page:number;totalPages:number}
