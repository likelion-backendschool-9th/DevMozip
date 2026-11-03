export interface FollowPreferences { activity:boolean;projects:boolean }
export interface FollowNotice { id:string;recipientId:string;actorId:string;kind:'activity'|'project';recordId:string;title:string;createdAt:string }
